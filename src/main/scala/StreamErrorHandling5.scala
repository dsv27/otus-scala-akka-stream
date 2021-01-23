import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import scala.concurrent.duration._
import scala.concurrent.Future

object StreamErrorHandling5 extends App {
  implicit val system      = ActorSystem()
  implicit val materiliser = ActorMaterializer
  implicit val ec          = system.dispatcher

  def printSink(label: String): Sink[Any, Future[Done]] = Sink.foreach((x: Any) ⇒ println(s"$label:$x"))

  val runnableGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] ⇒
    import GraphDSL.Implicits._
    val in    = Source(1 to 20).throttle(1, 500.millis)
    val bcast = builder.add(Broadcast[Int](3))
    in ~> bcast.in
    bcast.out(0) ~> Flow[Int].log("#1Normal") ~> printSink("#1Normal")
    bcast.out(1) ~> Flow[Int].log("#2Normal") ~> printSink("#2Normal")
    bcast.out(2).map { x ⇒ if (x == 4) throw new RuntimeException("I'm fallen and i can't get up"); x } ~> Flow[Int].log("#3Error") ~> printSink("#3Error")

    ClosedShape
  }
                                              )

  val initialFlow = Flow.fromFunction { x: Int ⇒ x * 2 }
  runnableGraph.run()
}
