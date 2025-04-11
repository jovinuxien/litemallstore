package org.linlinjava.litemall.library.exceptions;

public class InvalidEventTypeException extends RuntimeException {
    public InvalidEventTypeException(){
        super();
    }
    public InvalidEventTypeException(String eventType){
        super("Invalid event type: " + eventType);
    }
}
