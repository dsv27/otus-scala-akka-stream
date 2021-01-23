import akka._
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent._
import scala.concurrent.duration._

object FlatDsl4 extends App {
  implicit val system = ActorSystem()
  implicit val materiliser = ActorMaterializer
  implicit val ec = system.dispatcher

  val source: Source[Int, NotUsed] = Source(1 to 100)
  val stream: RunnableGraph[NotUsed] = source.mapAsync(5){x ⇒ Future{
    Thread.sleep(1000)
    println(s"Executed for: $x")
    x
  }
  } to Sink.ignore

  stream.run()
}
