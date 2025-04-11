package org.linlinjava.litemall.library.eventsource;

import org.linlinjava.litemall.library.Event;
import org.linlinjava.litemall.library.State;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public class EventHandlerBuilder <E extends Event, S extends State> {
    private Predicate<S> statePredicate;
    private Class<? extends E>  eventClass;
    private final Map<Class<? extends E>, EventHandler<? extends E, ? extends S>> handlersEntries = new HashMap<>();
    private final Map<Predicate<E>, BiFunction<S, E, S>> handlers = new HashMap<>();



    public <E1 extends E, S1 extends S> EventHandlerBuilder<E, S> onEvent(Class<E1> eventClass, EventHandler<E1, S1> hdler){
        handlersEntries.put(eventClass, hdler);
        return (EventHandlerBuilder<E, S>) this;
    }



    public EventHandler<E, S> build() {
        return (event, state) -> {
            for (Map.Entry<Predicate<E>, BiFunction<S, E, S>> pair : handlers.entrySet()) {
                if (statePredicate.test(state) && pair.getKey().test(event)) {
                    state = pair.getValue().apply(state, event);
                }
            }
            return state;
        };
    }

    public Map<Class<? extends E>, EventHandler<? extends E, ? extends S>> getEventHandlers(){
        return handlersEntries;
    }

}
