import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import akka.stream._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.util.ByteString
import scala.concurrent._
import scala.concurrent.duration._
import java.nio.file.Paths

object FlatDsl1 extends App {
  val source: Source[Int, NotUsed] = Source(1 to 100)
  val sink  : Sink[Int, Future[Done]] = Sink foreach println
  val stream: RunnableGraph[NotUsed] = source to sink
  implicit val system = ActorSystem()
  implicit val materiliser = ActorMaterializer
  stream.run()
}
