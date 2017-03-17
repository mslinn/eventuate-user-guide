package doc

/** Used by [[ConditionalRequestsLike]] */
object EventsourcedViews {
  //#event-sourced-view
  import akka.actor.ActorRef
  import com.rbmhtechnology.eventuate.{EventsourcedView, VectorTime}

  case class Appended(entry: String)
  case class Resolved(selectedTimestamp: VectorTime)

  case object GetAppendCount
  case class GetAppendCountReply(count: Long)

  case object GetResolveCount
  case class GetResolveCountReply(count: Long)

  class ExampleView(override val id: String, override val eventLog: ActorRef) extends EventsourcedView {
    private var appendCount: Long = 0L
    private var resolveCount: Long = 0L

    override def onCommand: PartialFunction[Any, Unit] = {
      case GetAppendCount => sender() ! GetAppendCountReply(appendCount)
      case GetResolveCount => sender() ! GetResolveCountReply(resolveCount)
    }

    override def onEvent: PartialFunction[Any, Unit] = {
      case Appended(_) => appendCount += 1L
      case Resolved(_) => resolveCount += 1L
    }
  }
  //#
}
