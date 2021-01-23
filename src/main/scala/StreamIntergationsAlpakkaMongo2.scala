import akka.actor.ActorSystem
import akka.stream.alpakka.mongodb.javadsl.MongoFlow
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import com.mongodb.client.model.Filters
import com.mongodb.reactivestreams.client._
import org.bson._

import scala.concurrent.duration._
import scala.concurrent.Await


object StreamIntergationsAlpakkaMongo2 extends App {
  implicit val system      = ActorSystem()
  implicit val materiliser = ActorMaterializer
  implicit val ec          = system.dispatcher


  private val client  = MongoClients.create("mongodb://localhost:27017")
  private val db      = client.getDatabase("akka_http")
  private val fooColl = db.getCollection("foos")

  val runnable1 =
    Source(1 to 1000)
      .map { x ⇒ Filters.eq("_id", x) }
      .flatMapMerge(10, { x ⇒ Source.fromPublisher(fooColl.find(x).first()) })
      .runFold(Vector.empty[Document]) { (acc, elem) ⇒ acc :+ elem }

  val runnable2 =
    Source(1 to 1000)
      .map { x ⇒ Filters.eq("_id", x) }
      .flatMapConcat { x ⇒ Source.fromPublisher(fooColl.find(x).first()) }
      .runFold(Vector.empty[Document]) { (acc, elem) ⇒ acc :+ elem }

  val docs1 = Await.result(runnable1, 30.seconds)
  val docs2 = Await.result(runnable2, 30.seconds)
  println(s"found 1, ${docs1.length}")
  println(s"found 2, ${docs2.length}")
  system.terminate()
}
