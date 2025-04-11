package org.linlinjava.litemall.library;

import java.util.List;

public interface EventBus {
    void publish(List<Event> events);
}
