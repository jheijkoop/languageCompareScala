import spray.json._
import DefaultJsonProtocol._

object ApiQuestionor extends App {
  val bla = scala.io.Source.fromURL("http://api.openweathermap.org/data/2.5/forecast/daily?q=London&mode=xml&units=metric&cnt=7").mkString
  bla.parseJson
}
