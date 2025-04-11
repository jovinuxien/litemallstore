package org.linlinjava.litemall.library.exceptions;

public class InvalidEventException extends RuntimeException {

    public InvalidEventException(){
        super();
    }
    public InvalidEventException(String message){
        super("Invalid event: " + message);
    }
}
