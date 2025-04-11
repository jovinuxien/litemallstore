package org.linlinjava.litemall.order.application.util.exception.groupon;

public class LitemallGrouponFullException extends RuntimeException  {

    public LitemallGrouponFullException(){
        super();
    }

    public LitemallGrouponFullException(String message){
        super("Groupon full: " + message);
    }

    public LitemallGrouponFullException(String message, Throwable cause) {
        super(message, cause);
    }
}
