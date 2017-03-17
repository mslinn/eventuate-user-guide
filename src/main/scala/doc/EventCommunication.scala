package doc

/** Not used by any app. Why is it here? */
object EventCommunication {
  import akka.actor._

  var system: ActorSystem = _
  var eventLog: ActorRef = _

  //#event-driven-communication
  // some imports omitted ...
  import com.rbmhtechnology.eventuate.EventsourcedView.Handler
  import com.rbmhtechnology.eventuate.{EventsourcedActor, PersistOnEvent}

  case class Ping(num: Int)
  case class Pong(num: Int)

  class PingActor(val id: String, val eventLog: ActorRef, completion: ActorRef)
    extends EventsourcedActor with PersistOnEvent {

    override def onCommand: PartialFunction[Any, Unit] = {
      case "serve" => persist(Ping(1))(Handler.empty)
    }

    override def onEvent: PartialFunction[Any, Unit] = {
      case Pong(10) if !recovering => completion ! "done"
      case Pong(i)  => persistOnEvent(Ping(i + 1))
    }
  }

  class PongActor(val id: String, val eventLog: ActorRef)
    extends EventsourcedActor with PersistOnEvent {

    override def onCommand: PartialFunction[Any, Unit] = {
      case _ =>
    }
    override def onEvent: PartialFunction[Any, Unit] = {
      case Ping(i) => persistOnEvent(Pong(i))
    }
  }

  val pingActor: ActorRef = system.actorOf(Props(new PingActor("ping", eventLog, system.deadLetters)))
  val pongActor: ActorRef = system.actorOf(Props(new PongActor("pong", eventLog)))

  pingActor ! "serve"
  //#
}
