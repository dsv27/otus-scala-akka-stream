import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.duration._

object FlatDsl6 extends App {
  implicit val system      = ActorSystem()
  implicit val materiliser = ActorMaterializer
  implicit val ec          = system.dispatcher

  case object Complete

  case object Fail

  val stream =
    Source.actorRef[Int](
      completionMatcher = { case Complete ⇒ CompletionStrategy.draining }: PartialFunction[Any, CompletionStrategy],
      failureMatcher = { case Fail ⇒ new RuntimeException("I've failed. And I can't get up") }: PartialFunction[Any, Throwable],
      bufferSize = 10,
      overflowStrategy = OverflowStrategy.dropHead,
    ).throttle(1, 1.second).to(Sink.foreach(println))


  val ref = stream.run()
  (1 to 100).foreach(ref.tell(_, null))
}
