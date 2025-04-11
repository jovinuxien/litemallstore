package org.linlinjava.litemall.library;

import org.linlinjava.litemall.library.aggregate.AggregateRoot;

import java.util.List;

public interface EventStoreDB {

    void saveEvents(List<Event> events);

    List<Event> loadEvents(final String aggregateId, long version);

    <T extends AggregateRoot> void save(final T aggregate);

    <T extends AggregateRoot> T load(final String aggregate, final Class<T> aggregateType);


    Boolean exists(final String aggregate);

}