package org.linlinjava.litemall.library.exceptions;

public class AggregateNotFoundException extends RuntimeException {
    public AggregateNotFoundException() {
        super();
    }
    public AggregateNotFoundException(String aggregateId) {
        super("Aggregate not found id: " + aggregateId);
    }
}
