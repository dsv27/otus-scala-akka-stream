import akka._
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object FlatDsl2 extends App {
  implicit val system      = ActorSystem()
  implicit val materiliser = ActorMaterializer
  implicit val ec          = system.dispatcher
  val source: Source[Int, NotUsed]         = Source(1 to 30)
  val sink1 : Sink[Int, Future[List[Int]]] = Sink.fold[List[Int], Int](List.empty[Int]) { case (acc, i) ⇒ i :: acc }
  val stream                               = source.toMat(sink1)(Keep.right)
  val v = Await.result(stream.run(), 10.seconds)
  println(v)
  Await.result(system.terminate(), 10.seconds)

}
