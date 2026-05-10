package org.linlinjava.litemall.order.domain.model.domainservices.groupon;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;

import java.util.Objects;

public class LitemallGrouponJoinResult {

    public enum JoinStatus {
        SUCCESS_NEW_GROUPON_CREATED,
        SUCCESS_JOINED_EXISTING_GROUPON,
        SUCCESS_GROUPON_COMPLETED, // Immediate completion if joining filled the groupon
        FAILED_VALIDATION_ERROR,
        FAILED_SYSTEM_ERROR,
        FAILED_CONCURRENCY_CONFLICT
    }

    @Getter
    private final JoinStatus status;
    @Getter
    private final String message;
    private final LitemallGrouponId activityId;
    @Getter
    private final String shareUrl;
    @Getter
    private final Integer currentParticipants;
    @Getter
    private final Integer requiredParticipants;
    @Getter
    private final boolean immediateCompletion;

    // Private constructor - use static factory methods
    private LitemallGrouponJoinResult(JoinStatus status, String message, LitemallGrouponId activityId,
                                      String shareUrl, Integer currentParticipants, Integer requiredParticipants,
                                      boolean immediateCompletion) {
        this.status = status;
        this.message = message;
        this.activityId = activityId;
        this.shareUrl = shareUrl;
        this.currentParticipants = currentParticipants;
        this.requiredParticipants = requiredParticipants;
        this.immediateCompletion = immediateCompletion;
    }

    // --- Static Factory Methods for Success Results ---

    public static LitemallGrouponJoinResult successNewActivity(LitemallGrouponId activityId, String shareUrl,
                                                               Integer requiredParticipants) {
        return new LitemallGrouponJoinResult(JoinStatus.SUCCESS_NEW_GROUPON_CREATED,
                "Successfully created new groupon activity", activityId, shareUrl, 1,
                requiredParticipants, false);
    }

    public static LitemallGrouponJoinResult successJoinedExisting(LitemallGrouponId activityId, String shareUrl,
                                                                  Integer currentParticipants, Integer requiredParticipants) {
        return new LitemallGrouponJoinResult(JoinStatus.SUCCESS_JOINED_EXISTING_GROUPON,
                "Successfully joined existing groupon activity", activityId, shareUrl,
                currentParticipants, requiredParticipants, false);
    }

    public static LitemallGrouponJoinResult successGrouponCompleted(LitemallGrouponId activityId,
                                                                    Integer requiredParticipants) {
        return new LitemallGrouponJoinResult(JoinStatus.SUCCESS_GROUPON_COMPLETED,
                "Groupon activity completed successfully", activityId, null,
                requiredParticipants, requiredParticipants, true);
    }

    // --- Static Factory Methods for Failure Results ---

    public static LitemallGrouponJoinResult failedValidation(String message) {
        return new LitemallGrouponJoinResult(JoinStatus.FAILED_VALIDATION_ERROR, message,
                null, null, null, null, false);
    }

    public static LitemallGrouponJoinResult failedSystemError(String message) {
        return new LitemallGrouponJoinResult(JoinStatus.FAILED_SYSTEM_ERROR, message,
                null, null, null, null, false);
    }

    public static LitemallGrouponJoinResult failedConcurrencyConflict() {
        return new LitemallGrouponJoinResult(JoinStatus.FAILED_CONCURRENCY_CONFLICT,
                "Groupon activity was modified by another user. Please try again.",
                null, null, null, null, false);
    }

    // --- Domain Logic & Accessors ---

    public boolean isSuccess() {
        return status == JoinStatus.SUCCESS_NEW_GROUPON_CREATED ||
                status == JoinStatus.SUCCESS_JOINED_EXISTING_GROUPON ||
                status == JoinStatus.SUCCESS_GROUPON_COMPLETED;
    }

    public boolean isNewActivityCreated() {
        return status == JoinStatus.SUCCESS_NEW_GROUPON_CREATED;
    }

    public boolean isJoinedExisting() {
        return status == JoinStatus.SUCCESS_JOINED_EXISTING_GROUPON;
    }

    public boolean isGrouponCompleted() {
        return status == JoinStatus.SUCCESS_GROUPON_COMPLETED;
    }

    public boolean needsSharing() {
        return isSuccess() && !isGrouponCompleted() && shareUrl != null;
    }

    public Integer getRemainingParticipants() {
        if (currentParticipants == null || requiredParticipants == null) {
            return null;
        }
        return Math.max(0, requiredParticipants - currentParticipants);
    }

    public Double getCompletionPercentage() {
        if (currentParticipants == null || requiredParticipants == null || requiredParticipants == 0) {
            return 0.0;
        }
        return (currentParticipants.doubleValue() / requiredParticipants.doubleValue()) * 100.0;
    }


    public LitemallGrouponId getActivityId() {
        if (!isSuccess()) {
            throw new IllegalStateException("No activity ID for failed join result");
        }
        return activityId;
    }



    // --- Value Object Contract ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LitemallGrouponJoinResult that = (LitemallGrouponJoinResult) o;
        return immediateCompletion == that.immediateCompletion &&
                status == that.status &&
                Objects.equals(message, that.message) &&
                Objects.equals(activityId, that.activityId) &&
                Objects.equals(shareUrl, that.shareUrl) &&
                Objects.equals(currentParticipants, that.currentParticipants) &&
                Objects.equals(requiredParticipants, that.requiredParticipants);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, message, activityId, shareUrl, currentParticipants,
                requiredParticipants, immediateCompletion);
    }

    @Override
    public String toString() {
        return "GrouponJoinResult{" +
                "status=" + status +
                ", message='" + message + '\'' +
                ", activityId=" + activityId +
                ", shareUrl='" + shareUrl + '\'' +
                ", currentParticipants=" + currentParticipants +
                ", requiredParticipants=" + requiredParticipants +
                ", immediateCompletion=" + immediateCompletion +
                '}';
    }
}
