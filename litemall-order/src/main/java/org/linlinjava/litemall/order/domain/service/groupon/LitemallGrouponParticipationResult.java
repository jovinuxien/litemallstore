package org.linlinjava.litemall.order.domain.service.groupon;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

public class LitemallGrouponParticipationResult {

    public enum ParticipationStatus {
        VALID,
        ALREADY_JOINED_SPECIFIC,
        ALREADY_PARTICIPATED_RULES,
        OWN_GROUPON_RESTRICTION,
        RULES_INVALID,
        GROUPON_FULL
    }

    private final ParticipationStatus status;
    private final String message;
    private final boolean allowed;

    private LitemallGrouponParticipationResult(ParticipationStatus status, String message, boolean allowed) {
        this.status = status;
        this.message = message;
        this.allowed = allowed;
    }

    public static LitemallGrouponParticipationResult valid() {
        return new LitemallGrouponParticipationResult(ParticipationStatus.VALID, "User can participate", true);
    }

    public static LitemallGrouponParticipationResult alreadyJoined(String message) {
        return new LitemallGrouponParticipationResult(ParticipationStatus.ALREADY_JOINED_SPECIFIC, message, false);
    }

    public static LitemallGrouponParticipationResult alreadyParticipated(String message) {
        return new LitemallGrouponParticipationResult(ParticipationStatus.ALREADY_PARTICIPATED_RULES, message, false);
    }

    public static LitemallGrouponParticipationResult ownGrouponRestriction(String message) {
        return new LitemallGrouponParticipationResult(ParticipationStatus.OWN_GROUPON_RESTRICTION, message, false);
    }

    public static LitemallGrouponParticipationResult failed(String message) {
        return new LitemallGrouponParticipationResult(ParticipationStatus.RULES_INVALID, message, false);
    }

    // Getters
    public boolean isAllowed() { return allowed; }
    public ParticipationStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
