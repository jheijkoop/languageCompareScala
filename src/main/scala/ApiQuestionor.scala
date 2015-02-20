import akka.actor._
import spray.json._

import scala.concurrent.{Await, Future, Promise, blocking}
import scala.io.Source
import scala.util.{Success, Try}

object ApiQuestionor extends App {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._

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

  class Collector(val system: ActorSystem) extends Actor {
    var responses: List[JsValue] = List()

    def shuttingDown(): Receive = {
      system.scheduler.scheduleOnce(100.milliseconds, self, "timeout")

      {
        case "timeout" => system.shutdown()
        case _ =>
      }
    }

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
        context.children.foreach(context.stop)
        println(responses)
        context.become(shuttingDown())
    }
  }

  class Downloador extends Actor {
    def receive = {
      case url: String =>
        val collector = sender
        download(url).onSuccess { case json => collector ! json }
    }
  }

  def withActors(system: ActorSystem) = {
    val collector = system.actorOf(Props(classOf[Collector], system))
    collector ! urls
  }

  def withPromises() {
    val promises = urls.map { url: String =>
      val promise = Promise[JsValue]

      download(url).onComplete(promise.tryComplete)

      promise
    }

    val succeeded = promises
        .map(promise => Try(Await.result(promise.future, 30.milliseconds)))
        .collect { case Success(json) => json }

    succeeded.foreach(println)
  }

  withPromises()
  withActors(ActorSystem("foobar"))
}
