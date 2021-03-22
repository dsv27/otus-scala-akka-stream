package homework
import homework.Model._
import io.circe.{Decoder, Encoder}
import stellar.sdk._
import io.circe.syntax._
import io.circe.parser._
import com.typesafe.scalalogging._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import stellar.sdk.model.{HorizonOrder, HorizonCursor, Record}

import akka.stream.alpakka.mongodb.scaladsl.{MongoFlow, MongoSource, MongoSink}

import com.mongodb.reactivestreams.client.{MongoCollection, MongoClients}
import com.mongodb.client.model.{Projections, Sorts, Field, BsonField}

import org.bson.codecs.configuration.CodecRegistries.{
  fromProviders,
  fromRegistries
}
import org.mongodb.scala.bson.codecs._
import org.mongodb.scala.bson.codecs.Macros._
import org.bson.Document

import akka._
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.Attributes

import scala.concurrent._
import scala.util.matching.Regex
import scopt.OParser
import stellar.sdk.model.response.NetworkInfo
import shapeless.ops.zipper
import scala.util.Try
import scala.collection.immutable.NumericRange

case class Config(
    start: Long = -1L,
    end: Long = -1L
)

object Main extends App with LazyLogging {

  /** ETL процесс загрузки с TestNetwork
    * --start - начальное значение Ledger для загрузки
    * --end - конечное значение Ledger для загрузки
    * Без параметров для загрузки берется последнее сохраненое значение в базе данных, и текущее в TestNetwork
    */
  implicit val network = TestNetwork
  implicit val system = ActorSystem()
  implicit val materiliser = ActorMaterializer
  implicit val ec = system.dispatcher

  def asDocument[T: Encoder](t: T): Document = {
    Document.parse(t.asJson.noSpaces)
    //Document(t.asJson.noSpaces)
  }

  def fromDocument[T: Decoder](doc: Document): Either[Throwable, T] = {
    parse(doc.toJson).flatMap(_.as[T])
  }

  /** Получение максимального значения Ledger записанного в БД
    *
    * @param ledgerColl Колекция в БД
    * @param ledger значение Ledger которе вернется если в БД нет записанной инофрмации
    * @return
    */
  def LedgerIdGetFromDB(
      ledgerColl: MongoCollection[Document],
      ledgerMax: Future[NetworkInfo],
      ledger: Long
  ) = {

    val ledgerMaxSource =
      Source.fromFuture(ledgerMax).map(v => v.latestLedger)
    val mngSource = MongoSource(
      ledgerColl
        .find()
        .sort(Sorts.descending("ledgerResponse.sequence"))
        .projection(
          Projections.fields(
            Projections.include("ledgerResponse.sequence"),
            Projections.excludeId()
          )
        )
        .first
    ).map { a =>
      a.get("ledgerResponse").asInstanceOf[Document] match {
        case v1 if (v1.isEmpty) => ledger
        case v2                 => v2.get("sequence").asInstanceOf[Int].toLong
      }
    }
    mngSource.zip(ledgerMaxSource)
  }

  /** Получение пакета данных согласно  Ledger, работает в пакетном режиме
    *
    * @param ledgerId Значение Ledger, по которому будут выбиратся данные
    * @return Пакет данных
    */
  def LedgerStatusGetBatch(ledgerId: Long) = {
    for {
      l ← network.ledger(ledgerId).map(LedgerWrapper(_))
      t ← network
        .transactionsByLedger(ledgerId)
        .map { _.toList }
        .map(tr => tr.map(TransactionWrapper(_)))
      o ← network
        .operationsByLedger(ledgerId)
        .map { _.toList }
        .map(op => op.map(OperationWrapper(_)))
      e ← network
        .effectsByLedger(ledgerId)
        .map { _.toList }
        .map(ef => ef.map(EffectWrapper(_)))
    } yield (l, t, o, e)
  }

  /** Завершение процесса по окончанию данных
    *
    * @param h Состояние стрима
    * @return 
    */
  def endStream(h: Try[Done]): Unit = {
    if (h.isSuccess) {
      logger.debug("Normal stream shutdown operation.")
    } else {
      logger.error("Emergency stream shutdown operation.")
    }
  }
  
  val cmdBuilder = OParser.builder[Config]
  val cmdParser = {
    import cmdBuilder._
    OParser.sequence(
      programName("etl"),
      opt[Long]('s', "start")
        .action((x, c) => c.copy(start = x))
        .text("With this value, we will start the load data")
        .required(),
      opt[Long]('e', "end")
        .action((x, c) => c.copy(end = x))
        .text("At this value, we will finish the load data")
        .required()
    )
  }

  private val client =
    MongoClients.create("mongodb://root:rooTtest@localhost:27017")
  private val db = client.getDatabase("stellar_db")
  private val ledgerColl = db.getCollection("ledgers")
  private val transactionsColl = db.getCollection("transactions")
  private val operationColl = db.getCollection("operation")
  private val effectsColl = db.getCollection("effects")

  // Определяем диапазон загрузки, из командной строки или из базы данных и состояния на сервере

  val ledgerLoad =
    OParser.parse(cmdParser, args, Config()) match {
      case Some(config) =>
        Source(Seq(config.start)).zip(Source(Seq(config.end)))
      case _ =>
        //val ledgerIdMax = Await.result(network.info(), 30.seconds).latestLedger
        LedgerIdGetFromDB(ledgerColl, network.info(), 10L)
    }

  // Настройка графа выполнения
  val runnableGraphBatch = RunnableGraph.fromGraph(GraphDSL.create() {
    implicit builder: GraphDSL.Builder[NotUsed] ⇒
      import GraphDSL.Implicits._

      val getLadger  = ledgerLoad.flatMapConcat(x => Source(x._1 to x._2))
        .mapAsync(1) { ledgerId => LedgerStatusGetBatch(ledgerId) }
        .log(name = "Read ledger data")
        .addAttributes(
          Attributes.logLevels(
            onElement = Attributes.LogLevels.Off,
            onFinish = Attributes.LogLevels.Info,
            onFailure = Attributes.LogLevels.Error
          )
        )

      val insertLenger = MongoFlow
        .insertOne(ledgerColl)
        .log(name = "Insert ledger")
        .addAttributes(
          Attributes.logLevels(
            onElement = Attributes.LogLevels.Off,
            onFinish = Attributes.LogLevels.Info,
            onFailure = Attributes.LogLevels.Error
          )
        )
      val insertTransaction = MongoFlow
        .insertMany(transactionsColl)
        .log(name = "Insert transactions")
        .addAttributes(
          Attributes.logLevels(
            onElement = Attributes.LogLevels.Off,
            onFinish = Attributes.LogLevels.Info,
            onFailure = Attributes.LogLevels.Error
          )
        )
      val insertOperation = MongoFlow
        .insertMany(operationColl)
        .log(name = "Insert operation")
        .addAttributes(
          Attributes.logLevels(
            onElement = Attributes.LogLevels.Off,
            onFinish = Attributes.LogLevels.Info,
            onFailure = Attributes.LogLevels.Error
          )
        )
      val insertEffects = MongoFlow
        .insertMany(effectsColl)
        .log(name = "Insert effects")
        .addAttributes(
          Attributes.logLevels(
            onElement = Attributes.LogLevels.Off,
            onFinish = Attributes.LogLevels.Info,
            onFailure = Attributes.LogLevels.Error
          )
        )
        def toSeq[T](x:T) : Seq[T] = Seq[T](x)

      val seqFlow = Flow.fromFunction(toSeq[Document])
      val mergeFlow = builder.add(Merge[Seq[Document]](4))
      val endSink = Sink.onComplete(endStream)

      val bcast = builder.add(
        Broadcast[
          (
              Model.LedgerWrapper,
              List[Model.TransactionWrapper],
              List[Model.OperationWrapper],
              List[Model.EffectWrapper]
          )
        ](4)
      )
      getLadger ~> bcast.in
      bcast.out(0).map(data => asDocument(data._1)) ~> insertLenger ~> seqFlow ~> mergeFlow
        .in(0)
      bcast
        .out(1)
        .map(data =>
          data._2.map(tr => asDocument(tr))
        ) ~> insertTransaction ~> mergeFlow.in(1)
      bcast
        .out(2)
        .map(data =>
          data._3.map(op => asDocument(op))
        ) ~> insertOperation ~> mergeFlow.in(2)
      bcast
        .out(3)
        .map(data =>
          data._4.map(ef => asDocument(ef))
        ) ~> insertEffects ~> mergeFlow.in(3)
      mergeFlow.out ~> endSink
      ClosedShape
  })

  runnableGraphBatch.run()
  client.close()

}
