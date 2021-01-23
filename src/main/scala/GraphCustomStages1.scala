import akka._
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}

import scala.concurrent._
import scala.concurrent.duration._

object GraphCustomStages1 extends App{
  class FibSource extends GraphStage[SourceShape[Int]] {
    val out: Outlet[Int] = Outlet("FibonacciSource")
    override val shape: SourceShape[Int] = SourceShape(out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        // state here

        private var prevPrev = 1
        private var prev = 1

        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            val curr = prev + prevPrev
            push(out, curr)
            prevPrev = prev
            prev = curr
          }
        })
      }
  }
  def fibs = Source.combine(Source(1 :: 1 :: Nil), Source.fromGraph(new FibSource))(Concat(_))
  implicit val system      = ActorSystem()
  implicit val materiliser = ActorMaterializer
  implicit val ec          = system.dispatcher
  val stream = fibs.take(10) to Sink.foreach(println)
  stream.run()
  Await.result(system.terminate(), 10.seconds)


}
