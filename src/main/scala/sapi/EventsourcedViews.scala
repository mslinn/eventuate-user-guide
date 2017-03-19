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

/** Used by [[ConditionalExample]] */
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
