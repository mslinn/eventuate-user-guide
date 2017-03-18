package doc

import akka.actor.ActorSystem

object Util {
  // Pause for messages to be displayed before shutting down Akka
  def pauseThenStop(seconds: Int = 1)(implicit system: ActorSystem): Unit = {
    import scala.concurrent.duration._
    import system.dispatcher
    import scala.language.postfixOps
    system.scheduler.scheduleOnce(seconds seconds) {
      system.terminate()
      ()
    }
    ()
  }
}
