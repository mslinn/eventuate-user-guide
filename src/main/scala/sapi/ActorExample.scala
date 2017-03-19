/*
 * Copyright 2015 - 2017 Red Bull Media House GmbH <http://www.redbullmediahouse.com> and Mike Slinn - all rights reserved.
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

package sapi

object ActorExample extends App {
  //#event-sourced-actor
  import akka.actor._
  import com.rbmhtechnology.eventuate.EventsourcedActor
  import scala.util._

  // Commands
  case object Print
  case class Append(entry: String)

  // Command replies
  case class AppendSuccess(entry: String)
  case class AppendFailure(cause: Throwable)

  // Event
  case class Appended(entry: String)

  class ExampleActor(
    override val id: String,
    override val aggregateId: Option[String],
    override val eventLog: ActorRef
  ) extends EventsourcedActor {
    // TODO this would be better as an ArrayBuffer
    private var currentState: Vector[String] = Vector.empty

    override def onCommand: PartialFunction[Any, Unit] = {
      case Print =>
        println(s"[id = $id, aggregate id = ${ aggregateId.getOrElse("<undefined>")}] ${ currentState.mkString(",") }")

      case Append(entry) => persist(Appended(entry)) {
        case Success(_)   => sender() ! AppendSuccess(entry)
        case Failure(err) => sender() ! AppendFailure(err)
      }
    }

    override def onEvent: PartialFunction[Any, Unit] = {
      case Appended(entry) => currentState = currentState :+ entry
    }
  }
  //#

  //#create-one-instance
  import com.rbmhtechnology.eventuate.log.leveldb.LeveldbEventLog
  import com.rbmhtechnology.eventuate.ReplicationConnection._

  // `DefaultRemoteSystemName` is defined as "location" in the
  // [[ReplicationConnection]] object, so the ActorSystem name
  // is "location"
  implicit val system: ActorSystem = ActorSystem(DefaultRemoteSystemName)
  //#

  //#create-one-instance

  // Wrap a new instance of a `LeveldbEventLog` configuration object with
  // log id "qt-1" into an Actor.
  // This creates a log directory called `target/log-qt-1/`
  val eventLog: ActorRef = system.actorOf(LeveldbEventLog.props("qt-1"))
  //#

  //#create-one-instance

  // Create a new instance of ExampleActor with id=="1" and aggregateId==Some("a");
  // also provide the eventLog [[ActorRef]] to the actorOf [[Actor]] factory
  val ea1 = system.actorOf(Props(new ExampleActor("1", Some("a"), eventLog)))

  ea1 ! Append("a")
  ea1 ! Append("b")
  //#

  //#print-one-instance
  ea1 ! Print
  //#

  //#create-two-instances
  val b2: ActorRef =
    system.actorOf(Props(new ExampleActor("2", Some("b"), eventLog)))
  val c3: ActorRef =
    system.actorOf(Props(new ExampleActor("3", Some("c"), eventLog)))

  b2 ! Append("a")
  b2 ! Append("b")

  c3 ! Append("x")
  c3 ! Append("y")
  //#

  //#print-two-instances
  b2 ! Print
  c3 ! Print
  //#

  //#create-replica-instances
  // created at location 1
  val d4 = system.actorOf(Props(new ExampleActor("4", Some("d"), eventLog)))

  // created at location 2
  val d5 = system.actorOf(Props(new ExampleActor("5", Some("d"), eventLog)))

  d4 ! Append("a")
  //#

  Thread.sleep(1000)

  d4 ! Print // fixme why is this not referenced in user-guide.rst?
  d5 ! Print

  //#send-another-append
  d5 ! Append("b")
  //#

  Thread.sleep(1000)

  d4 ! Print
  d5 ! Print

  Util.pauseThenStop()
}
