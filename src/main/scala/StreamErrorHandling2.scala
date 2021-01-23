import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._

object StreamErrorHandling2 extends App {
  implicit val system      = ActorSystem()
  implicit val materiliser = ActorMaterializer
  implicit val ec          = system.dispatcher

  Source(1 to 10).log("before").map{ x ⇒
    if(x == 5)  throw new RuntimeException("I've failed. And I can't get up")
    x
  }.log("after", x ⇒ x).to(Sink.foreach(println)).run()

  system.terminate()
}
