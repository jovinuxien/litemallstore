package org.linlinjava.litemall.library.effect;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class Effect <Event, State> {

    public static <Event, State> Persist<Event, State> persist(Event... events) {
     return new Persist<>(Arrays.asList(events));
    }

    public static <Event, State> Persist<Event, State> persist(Collection<Event> events) {
        return new Persist<>(events.stream().toList());
    }

    public static <Event, State> None<Event, State> none() {
       return new None<>();
    }


    /**
     * -------------------------our public static class------------------------
     */

    /**
     *
     * @param <Event>
     * @param <State>
     */
    public static final class Persist<Event, State> extends Effect<Event, State> {
        private final List<Event> events;


        public Persist(List<Event> evts) {
          this.events = evts;
        }

        public List<Event> events(){
            return events;
        }


        /**
         * @  the purpose of this handler is to perform the logic on a State after persisting the event
         * @param listAgentToReplyTo
         * @param messageHandlerForReply
         * @return the returned type specify the type of the message
         * @param <R>
         */
        public <R> Persist<Event, State> thenReply(List<R> listAgentToReplyTo, Function<State, R> messageHandlerForReply){
            return this;
        }

        /**
         *
         * @param callback
         * @return
         */
        public Persist<Event, State> thenRun(Consumer<State> callback){
            return this;
        }

    }

    /**
     *
     * @param <Event>
     * @param <State>
     */
    public static final class None<Event, State> extends Effect<Event, State> {
        public static final  None<Object, Object> INSTANCE  =  new None<Object, Object>();
    }
}
