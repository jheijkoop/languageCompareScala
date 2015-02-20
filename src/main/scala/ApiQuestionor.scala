import akka.actor._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future, Promise, blocking}
import scala.io.Source
import scala.util.Try

object ApiQuestionor extends App {
  import spray.json._

  import scala.concurrent.ExecutionContext.Implicits.global

  def download(url: String) = {
    Future {
      blocking {
        Source.fromURL(url).mkString.parseJson
      }
    }
  }

  val urls = List(
    "http://api.openweathermap.org/data/2.5/forecast/daily?q=London&units=metric&cnt=1",
    "http://api.openweathermap.org/data/2.5/forecast/daily?q=London&units=metric&cnt=7",
    "http://api.openweathermap.org/data/2.5/forecast/daily?q=London&units=metric&cnt=14"
  )

  // just futures
  val promises = urls.map { url: String =>
    val promise = Promise[JsValue]

    download(url).onComplete(promise.tryComplete)

    promise
  }

  promises.map(promise => Try(Await.result(promise.future, 30.milliseconds)))

  val succeeded = for {
    promise <- promises
    possibleResult: Try[JsValue] <- promise.future.value
    if possibleResult.isSuccess
  } yield possibleResult.get

  succeeded.foreach(println)
  // just futures

  // actors!!
  class Collector extends Actor {
    var responses: List[JsValue] = List()

    def shuttingDown: Receive = {
      case "timeout" => system.shutdown()
      case _ =>
    }

    import scala.concurrent.duration._
    def receive = {
      case urls: List[_]  =>
        urls.foreach {
          case url: String =>
            val worker = context.actorOf(Props(classOf[Downloador]))
            worker ! url
        }
        system.scheduler.scheduleOnce(30.milliseconds, self, "timeout")

      case response: JsValue => responses = response :: responses
      case "timeout" =>
        context.children.foreach(_ ! PoisonPill)
        println(responses)
        context.become(shuttingDown)
        system.scheduler.scheduleOnce(100.milliseconds, self, "timeout")
    }
  }

  class Downloador extends Actor {
    def receive = {
      case url: String =>
        val collector = sender
        download(url).onSuccess { case json => collector ! json }
    }
  }

  implicit val system = ActorSystem("foobar")
  val collector = system.actorOf(Props(new Collector))
  collector ! urls
  // actors!!
}
