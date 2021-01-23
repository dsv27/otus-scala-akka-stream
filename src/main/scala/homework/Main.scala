package homework
import io.circe.{Decoder, Encoder}
import org.bson.Document
import stellar.sdk._
import io.circe.syntax._
import io.circe.parser._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object Main extends App{
  implicit val network = TestNetwork

  def asDocument[T: Encoder](t: T): Document = {
    Document.parse(t.asJson.noSpaces)
  }

  def fromDocument[T: Decoder](doc: Document): Either[Throwable, T] = {
    parse(doc.toJson).flatMap(_.as[T])
  }

  val ledgerId = 1270863
  val v = for{
    l ← network.ledger(ledgerId)
    t ← network.transactionsByLedger(ledgerId).map{_.toList}
    o ← network.operationsByLedger(ledgerId).map{_.toList}
    e ← network.effectsByLedger(ledgerId).map{_.toList}
  } yield (l,t,o,e)

  val data = Await.result(v, 30.seconds)


  println(data)

}
