
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage._

import scala.concurrent._
import scala.concurrent.duration._

object GraphCustomStages2 extends App{
  class MultiplexFlow[T](val times: Int) extends GraphStage[FlowShape[T,T]] {
    val in: Inlet[T] = Inlet("MonoIn")
    val out: Outlet[T] = Outlet("DoubleOut")
    override val shape: FlowShape[T,T] = FlowShape.of(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        // state here

        setHandlers(in, out, new InHandler with OutHandler {

          private var t: Option[T] = None

          override def onPush(): Unit =
            if(t.isEmpty) {
              val tt = grab(in)
              if(isAvailable(out))
                emitMultiple(out, (1 to times) map {_ ⇒ tt})
              else
                t = Some(tt)
            }

          override def onPull(): Unit = {
            if(t.isDefined) {
              emitMultiple(out, (1 to times) map {_ ⇒ t.get})
              t = None
            }
            pull(in)
          }
        })

      }
  }

  implicit val system      = ActorSystem()
  implicit val materiliser = ActorMaterializer
  implicit val ec          = system.dispatcher
  val stream = Source(1 to 5).via(new MultiplexFlow[Int](3)).to(Sink.foreach(println))
  stream.run()
  Await.result(system.terminate(), 10.seconds)


}
