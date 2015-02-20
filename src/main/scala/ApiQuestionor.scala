import java.util.concurrent.TimeoutException

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Promise}
import scala.util.Try

object ApiQuestionor extends App {
  import spray.json._

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.{Future, blocking}

  def download(url: String) = {
    val result = scala.io.Source.fromURL(url).mkString
    result.parseJson
  }

  val urls = List(
    "http://api.openweathermap.org/data/2.5/forecast/daily?q=London&units=metric&cnt=1",
    "http://api.openweathermap.org/data/2.5/forecast/daily?q=London&units=metric&cnt=7",
    "http://api.openweathermap.org/data/2.5/forecast/daily?q=London&units=metric&cnt=14"
  )

  // Download image (blocking operation)
  val promises = urls.map { url: String =>
    val promise = Promise[JsValue]

    val future = Future {
      blocking {
        download(url)
      }
    }
    future.onComplete(promise.tryComplete)

    promise
  }

  promises.map(promise => Try(Await.result(promise.future, 30.milliseconds)))

  val succeeded = for {
    promise <- promises
    possibleResult: Try[JsValue] <- promise.future.value
    if possibleResult.isSuccess
  } yield possibleResult.get

  succeeded.foreach(println)
}
