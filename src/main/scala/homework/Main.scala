package homework
import homework.Model._
import io.circe.{Decoder, Encoder}
import stellar.sdk._
import io.circe.syntax._
import io.circe.parser._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import stellar.sdk.model.{HorizonOrder, HorizonCursor, Record}

import akka.stream.alpakka.mongodb.scaladsl.{MongoFlow, MongoSource, MongoSink}
import com.mongodb.reactivestreams.client.{MongoCollection, MongoClients}
import org.bson.Document

import akka._
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.Attributes

import scala.concurrent._
import scala.util.matching.Regex
import scopt.OParser

case class Config(
    start: Long = -1L,
    end: Long = -1L
)

object Main extends App {

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
  def LedgerIdGetFromBD(
      ledgerColl: MongoCollection[Document],
      ledger: Long = 0L
  ) = {
    val ledgerToRegex: Regex = """\{"sequence": (\d+)\}""".r
    val getMaxLenger = MongoSource(
      ledgerColl
        .find(new Document)
        .sort(Document.parse("{sequence: 1}"))
        .limit(1)
        .projection(Document.parse("{_id: 0, ledgerResponse: {sequence: 1} }"))
    )
    val rows =
      Await.result(getMaxLenger.runWith(Sink.seq), 30.second)
    if (rows.isEmpty)
      ledger
    else if (ledgerToRegex.findAllIn(rows.head.toJson()).group(1).isEmpty)
      ledger
    else
      ledgerToRegex.findAllIn(rows.head.toJson()).group(1).toLong
  }

  /** Получение пакета данных согласно  Ledger, работает в пакетном режиме
    *
    * @param ledgerId Значение Ledger, по которому будут выбиратся данные
    * @return Пакет данных
    */
  def LedgerStatusGetBatch(ledgerId: Long) = {
    val v2 = for {
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
    Await.result(v2, 20.second)
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

  val ledgerLoad: (Long, Long) =
    OParser.parse(cmdParser, args, Config()) match {
      case Some(config) =>
        (config.start, config.end)
      case _ =>
        val ledgerIdMax = Await.result(network.info(), 30.seconds).latestLedger
        (LedgerIdGetFromBD(ledgerColl, ledgerIdMax - 10), ledgerIdMax)
    }

  // Настройка графа выполнения
  val runnableGraphBatch = RunnableGraph.fromGraph(GraphDSL.create() {
    implicit builder: GraphDSL.Builder[NotUsed] ⇒
      import GraphDSL.Implicits._
      val in = Source(ledgerLoad._1 to ledgerLoad._2)
        .map(ledgerId => LedgerStatusGetBatch(ledgerId))
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
      val blackHole = Sink.foreach { x: Any ⇒ (x) }
      val printData = Sink.foreach { x: Any ⇒ println(s"Inserting :$x") }
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
      in ~> bcast.in
      bcast
        .out(0)
        .map(data => asDocument(data._1)) ~> insertLenger ~> blackHole
      bcast
        .out(1)
        .map(data =>
          data._2.map(tr => asDocument(tr))
        ) ~> insertTransaction ~> blackHole
      bcast
        .out(2)
        .map(data =>
          data._3.map(op => asDocument(op))
        ) ~> insertOperation ~> blackHole
      bcast
        .out(3)
        .map(data =>
          data._4.map(ef => asDocument(ef))
        ) ~> insertEffects ~> blackHole
      ClosedShape
  })

  runnableGraphBatch.run()

  system.terminate()
  client.close()

}
