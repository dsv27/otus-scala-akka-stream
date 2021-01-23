import akka._
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._


object GraphNonlinearDsl2 extends App {
  implicit val system      = ActorSystem()
  implicit val materiliser = ActorMaterializer
  implicit val ec          = system.dispatcher

  def addExtra[I, O, Extra](oneToOneFlow: Flow[I, O, NotUsed]): Flow[(I, Extra), (O, Extra), NotUsed] = Flow.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] ⇒
    import GraphDSL.Implicits._
    val unzip                  = builder.add(Unzip[I, Extra])
    val zip                    = builder.add(Zip[O, Extra])
    val oneElementToOneElement = builder.add(oneToOneFlow)
    // (1, "tag for: 1")      // 1         // 1 -> 2                 // 2
                        unzip.out0      ~> oneElementToOneElement ~> zip.in0
                              // "tag for: 1"                        // "tag for: 1"
                        unzip.out1                                ~> zip.in1

    FlowShape[(I, Extra), (O, Extra)](unzip.in, zip.out)
  }
                                                                                                                       )

  val initialFlow   = Flow.fromFunction { x: Int ⇒ x * 2 }
  val runnableGraph =
    Source(1 to 10) map { x ⇒ (x, s"tag for: $x") } via
    addExtra(initialFlow) to
    Sink.foreach({ case (x: Int, y: String) => println(s"value:$x, tag:$y") })

  runnableGraph.run()
  system.terminate()
}
