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

/** Not referenced anywhere in `RBMHTechnology/eventuate`. Why is it here? */
object EventsourcedActorsUpdated {
  import akka.actor._
  import scala.util._

  case object Print
  case class Append(entry: String)
  case class AppendSuccess(entry: String)
  case class AppendFailure(cause: Throwable)
  case class Appended(entry: String)

  {
    //#detecting-concurrent-update
    import com.rbmhtechnology.eventuate.{EventsourcedActor, VectorTime}

    class ExampleActor(
      override val id: String,
      override val aggregateId: Option[String],
      override val eventLog: ActorRef
    ) extends EventsourcedActor {
      private var currentState: Vector[String] = Vector.empty
      private var updateTimestamp: VectorTime = VectorTime()

      override def onCommand: PartialFunction[Any, Unit] = {
        // ...
    //#
        case _ =>
    //#detecting-concurrent-update
    }

      override def onEvent: PartialFunction[Any, Unit] = {
        case Appended(entry2) =>
          if (updateTimestamp < lastVectorTimestamp) {
            // regular update
            currentState = currentState :+ entry2
            updateTimestamp = lastVectorTimestamp
          } else if (updateTimestamp conc lastVectorTimestamp) {
            // concurrent update
            // TODO: track conflicting versions
          }
      }
    }
    //#
  }

  {
    //#tracking-conflicting-versions
    import com.rbmhtechnology.eventuate.{ConcurrentVersions, EventsourcedActor, Versioned}
    import scala.collection.immutable.Seq

    class ExampleActor(
        override val id: String,
        override val aggregateId: Option[String],
        override val eventLog: ActorRef
      ) extends EventsourcedActor {
      private var versionedState: ConcurrentVersions[Vector[String], String] =
        ConcurrentVersions(Vector.empty, (s, a) => s :+ a)

      override def onCommand: PartialFunction[Any, Unit] = {
        // ...
    //#
        case _ =>
    //#tracking-conflicting-versions
      }

      override def onEvent: PartialFunction[Any, Unit] = {
        case Appended(entry) =>
          versionedState = versionedState.update(entry, lastVectorTimestamp)
          if (versionedState.conflict) {
            val conflictingVersions: Seq[Versioned[Vector[String]]] = versionedState.all
            // TODO: resolve conflicting versions
          } else {
            val currentState: Vector[String] = versionedState.all.head.value
            // ...
          }
      }
    }
    //#
  }

  {
    import com.rbmhtechnology.eventuate._

    //#automated-conflict-resolution
    class ExampleActor(
      override val id: String,
      override val aggregateId: Option[String],
      override val eventLog: ActorRef
    ) extends EventsourcedActor {

      private var versionedState: ConcurrentVersions[Vector[String], String] =
        ConcurrentVersions(Vector.empty, (s, a) => s :+ a)

      override def onCommand: PartialFunction[Any, Unit] = {
        // ...
    //#
        case _ =>
    //#automated-conflict-resolution
      }

      override def onEvent: PartialFunction[Any, Unit] = {
        case Appended(entry) =>
          versionedState = versionedState
            .update(entry, lastVectorTimestamp, lastSystemTimestamp, lastEmitterId)
          if (versionedState.conflict) {
            val conflictingVersions = versionedState.all.sortWith { (v1, v2) =>
              if (v1.systemTimestamp == v2.systemTimestamp) v1.creator < v2.creator
              else v1.systemTimestamp > v2.systemTimestamp
            }
            val winnerTimestamp: VectorTime = conflictingVersions.head.vectorTimestamp
            versionedState = versionedState.resolve(winnerTimestamp)
          }
      }
    }
    //#
  }

  {
    import com.rbmhtechnology.eventuate._
    import scala.collection.immutable.Seq

    //#interactive-conflict-resolution
    case class Append(entry: String)
    case class AppendRejected(entry: String, conflictingVersions: Seq[Versioned[Vector[String]]])

    case class Resolve(selectedTimestamp: VectorTime)
    case class Resolved(selectedTimestamp: VectorTime)

    class ExampleActor(
      override val id: String,
      override val aggregateId: Option[String],
      override val eventLog: ActorRef
    ) extends EventsourcedActor {

      private var versionedState: ConcurrentVersions[Vector[String], String] =
        ConcurrentVersions(Vector.empty, (s, a) => s :+ a)

      override def onCommand: PartialFunction[Any, Unit] = {
        case Append(entry) if versionedState.conflict =>
          sender() ! AppendRejected(entry, versionedState.all)

        case Append(entry) =>
          // ...

        case Resolve(selectedTimestamp) => persist(Resolved(selectedTimestamp)) {
          case Success(evt) => // reply to sender omitted ...
          case Failure(err) => // reply to sender omitted ...
        }
      }

      override def onEvent: PartialFunction[Any, Unit] = {
        case Appended(entry) =>
          versionedState = versionedState
            .update(entry, lastVectorTimestamp, lastSystemTimestamp, lastEmitterId)

        case Resolved(selectedTimestamp) =>
          versionedState = versionedState.resolve(selectedTimestamp, lastVectorTimestamp)
      }
    }
    //#
  }
}
