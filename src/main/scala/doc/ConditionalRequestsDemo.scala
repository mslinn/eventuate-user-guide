/*
 * Copyright 2015 - 2017 Red Bull Media House GmbH <http://www.redbullmediahouse.com> - all rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package doc

object ConditionalRequestsDemo extends App with ConditionalRequestsLike {
  Util.pauseThenStop()
}

trait ConditionalRequestsLike {
  import akka.actor._
  import com.rbmhtechnology.eventuate.ReplicationConnection
  import com.rbmhtechnology.eventuate.log.leveldb.LeveldbEventLog
  import doc.EventsourcedViews._

  implicit val system: ActorSystem = ActorSystem(ReplicationConnection.DefaultRemoteSystemName)
  val eventLog: ActorRef = system.actorOf(LeveldbEventLog.props("qt-2"))

  //#conditional-requests
  import akka.actor._
  import akka.pattern.ask
  import akka.util.Timeout
  import com.rbmhtechnology.eventuate._
  import scala.concurrent.duration._
  import scala.util._

  case class Append(entry: String)
  case class AppendSuccess(entry: String, updateTimestamp: VectorTime)

  class ExampleActor(override val id: String,
                     override val eventLog: ActorRef) extends EventsourcedActor {

    private var currentState: Vector[String] = Vector.empty
    override val aggregateId = Some(id)

    override def onCommand: PartialFunction[Any, Unit] = {
      case Append(entry) => persist(Appended(entry)) {
        case Success(_) =>
          sender() ! AppendSuccess(entry, lastVectorTimestamp)
        case Failure(_) =>
          // ...
      }
      // ...
    }

    override def onEvent: PartialFunction[Any, Unit] = {
      case Appended(entry) => currentState = currentState :+ entry
    }
  }

  class ExampleView(override val id: String, override val eventLog: ActorRef)
    extends EventsourcedView with ConditionalRequests {
    // ...
  //#
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
  //#conditional-requests
  }

  val ea: ActorRef = system.actorOf(Props(new ExampleActor("ea", eventLog)))
  val ev: ActorRef = system.actorOf(Props(new ExampleView("ev", eventLog)))

  import system.dispatcher
  implicit val timeout = Timeout(5.seconds)

  for {
    AppendSuccess(_, timestamp) <- ea ? Append("a")
    GetAppendCountReply(count)  <- ev ? ConditionalRequest(timestamp, GetAppendCount)
  } println(s"append count = $count")
  //#
}
