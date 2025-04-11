package org.linlinjava.litemall.library.eventsource;

import org.linlinjava.litemall.library.Event;
import org.linlinjava.litemall.library.State;

@FunctionalInterface
public interface EventHandler<E extends Event,  S extends State> {
    S applyEvent(E event, S state);
}
