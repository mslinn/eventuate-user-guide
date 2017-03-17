import akka.actor._
import com.rbmhtechnology.eventuate.EventsourcedActor
import scala.util._

// Commands
case object Print
case class Append(entry: String)

// Command replies
case class AppendSuccess(entry: String)
case class AppendFailure(cause: Throwable)

/** Event */
case class Appended(entry: String)

class ExampleActor(
  override val id: String,
  override val aggregateId: Option[String],
  override val eventLog: ActorRef
) extends EventsourcedActor {
  private var currentState: Vector[String] = Vector.empty

  override def onCommand: PartialFunction[Any, Unit] = {
    case Print =>
      println(s"[id = $id, aggregate id = ${ aggregateId.getOrElse("<undefined>") }] ${ currentState.mkString(",") }")

    case Append(entry) => persist(Appended(entry)) {
      case Success(evt) => sender() ! AppendSuccess(entry)
      case Failure(err) => sender() ! AppendFailure(err)
    }
  }

  override def onEvent: PartialFunction[Any, Unit] = {
    case Appended(entry) => currentState = currentState :+ entry
  }
}
