import akka._
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent._
import scala.concurrent.duration._

object FlatDsl3 extends App {
  implicit val system      = ActorSystem()
  implicit val materiliser = ActorMaterializer
  implicit val ec          = system.dispatcher
  val source: Source[Int, NotUsed]         = Source(1 to 20)
  val sink1 : Sink[Int, Future[List[Int]]] = Sink.fold[List[Int], Int](List.empty[Int]) { case (acc, i) ⇒ i :: acc }
  val sink2 : Sink[Int, NotUsed]           = (Flow fromFunction identity[Int]).throttle(1, 100.millis) to (Sink foreach println)
  val stream                               = source.alsoTo(sink2).toMat(sink1)(Keep.right)
  val v = Await.result(stream.run(), 10.seconds)
  println(v)
  Await.result(system.terminate(), 10.seconds)
}
