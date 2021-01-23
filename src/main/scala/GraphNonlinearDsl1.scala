import akka._
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent._
import scala.concurrent.duration._

object GraphNonlinearDsl1 extends App {
  implicit val system      = ActorSystem()
  implicit val materiliser = ActorMaterializer
  implicit val ec          = system.dispatcher

  val runnableGraph = RunnableGraph.fromGraph(GraphDSL.create(){ implicit builder: GraphDSL.Builder[NotUsed] ⇒
    import GraphDSL.Implicits._
    val in = Source(1 to 20)
    val out1 = Sink.foreach{x: Any ⇒ println(s"Even:$x")}
    val out2 = Sink.foreach{x: Any ⇒ println(s"Odd:$x")}
    val bcast = builder.add(Broadcast[Int](2))
    in ~> bcast.in
          bcast.out(0).filter(_ % 2 == 0) ~> out1
          bcast.out(1).filter(_ % 2 == 1) ~> out2

    ClosedShape
  })
  runnableGraph.run()
  system.terminate()
}
