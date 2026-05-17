package org.linlinjava.litemall.order.application.util.exception.groupon;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

public class LitemallAlreadyJoinGrouponException extends RuntimeException  {

    public LitemallAlreadyJoinGrouponException(String message) {
        super("Already join groupon: " + message);
    }
    public LitemallAlreadyJoinGrouponException() {
        super("You have already joined this groupon.");
    }

    public LitemallAlreadyJoinGrouponException(String message, Throwable cause) {
        super(message, cause);
    }
}
