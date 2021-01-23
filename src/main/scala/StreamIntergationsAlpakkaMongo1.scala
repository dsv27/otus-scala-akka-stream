import akka.actor.ActorSystem
import akka.stream.alpakka.mongodb.javadsl.MongoFlow
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import com.mongodb.reactivestreams.client._
import org.bson._
import scala.concurrent.duration._
import scala.concurrent.Await
import org.bson.conversions.Bson

object StreamIntergationsAlpakkaMongo1 extends App {
  implicit val system      = ActorSystem()
  implicit val materiliser = ActorMaterializer
  implicit val ec          = system.dispatcher


  private val client  = MongoClients.create("mongodb://localhost:27017")
  private val db      = client.getDatabase("akka_http")
  private val fooColl = db.getCollection("foos")

  val runnable =
    Source.futureSource(
      Source.fromPublisher(fooColl.drop()).run().map { _ ⇒
        Source(1 to 1000).map { x ⇒
          new Document()
            .append("_id", new BsonInt32(x))
            .append("foo", new BsonString(s"bar$x"))
        }
      }
      ).via(MongoFlow.insertOne[Document](fooColl)).toMat(Sink.foreach(println))(Keep.right)

  Await.result(runnable.run(), 30.seconds)
  println(s"done")
  system.terminate()
}
