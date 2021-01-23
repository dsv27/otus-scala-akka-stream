import akka._
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import scala.concurrent._
import scala.concurrent.duration._

object FlatDsl5 extends App {
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
      overflowStrategy = OverflowStrategy.fail,
    ).throttle(1, 1.second).to(Sink.foreach(println))


  val ref = stream.run()
  (1 to 5).foreach(ref.tell(_, null))
}
