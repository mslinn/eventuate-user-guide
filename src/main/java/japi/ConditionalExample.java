/*
 * Copyright 2015 - 2016 Red Bull Media House GmbH <http://www.redbullmediahouse.com> - all rights reserved.
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

package japi;

import static japi.DocUtils.append;
import japi.ViewExample.GetAppendCountReply;

//#conditional-requests
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.rbmhtechnology.eventuate.*;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.Future;
import scala.reflect.ClassTag;
import scala.reflect.ClassTag$;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static scala.compat.java8.JFunction.func;
import static scala.compat.java8.JFunction.proc;
//#

public class ConditionalExample {
  //#conditional-requests

  class ExampleActor extends AbstractEventsourcedActor {
    private final String id;
    private Collection<String> currentState = Collections.emptyList();

    public ExampleActor(String id, ActorRef eventLog) {
      super(id, eventLog);
      this.id = id;

      setOnCommand(ReceiveBuilder
        .match(Append.class, cmd -> persist(new Appended(cmd.entry), ResultHandler.onSuccess(
          evt -> sender().tell(new AppendSuccess(evt.entry, lastVectorTimestamp()), self())
        )))
        // ...
        .build());

      setOnEvent(ReceiveBuilder
        .match(Appended.class, evt -> currentState = append(currentState, evt.entry))
        .build());
    }

    @Override
    public Optional<String> getAggregateId() {
      return Optional.of(id);
    }
  }

  // Command
  public class Append {
    public final String entry;

    public Append(String entry) {
      this.entry = entry;
    }
  }

  // Command reply
  public class AppendSuccess {
    public final String entry;
    public final VectorTime updateTimestamp;

    public AppendSuccess(String entry, VectorTime updateTimestamp) {
      this.entry = entry;
      this.updateTimestamp = updateTimestamp;
    }
  }

  // Eventsourced-View
  class ExampleView extends AbstractEventsourcedView {

    // AbstractEventsourcedView has ConditionalRequests mixed-in by default

    public ExampleView(String id, ActorRef eventLog) {
      super(id, eventLog);

      // ...
    }
  }
  //#
  public static void main(String[] args) {
    final ActorSystem system = ActorSystem.create("");
    final ActorRef eventLog = null;
    final ExecutionContextExecutor dispatcher = system.dispatcher();

    //#conditional-requests

    ConditionalExample ce = new ConditionalExample();
    final ActorRef ea = system.actorOf(Props.create(ExampleActor.class, () -> ce.new ExampleActor("ea", eventLog)));
    final ActorRef ev = system.actorOf(Props.create(ExampleView.class, () -> ce.new ExampleView("ev", eventLog)));

    final Timeout timeout = Timeout.apply(5, TimeUnit.SECONDS);

    Patterns.ask(ea, ce.new Append("a"), timeout)
      .flatMap(func(m ->
          Patterns.ask(ev,
              new ConditionalRequest(((AppendSuccess) m).updateTimestamp, ce.new GetAppendCount()), timeout)
          )
        , dispatcher)
      .onComplete(proc(result -> {
        if (result.isSuccess()) {
          System.out.println("append count = " + ((GetAppendCountReply) result.get()).count);
        }
      }), dispatcher);

    //#
  }

  class GetAppendCount {}
}
