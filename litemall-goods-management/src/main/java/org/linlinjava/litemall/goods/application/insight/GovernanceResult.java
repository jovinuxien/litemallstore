package org.linlinjava.litemall.goods.application.insight;

/**
 * Wave-14 governance action outcome: {@code error != null} XOR {@code data != null}.
 * Mirrors {@link InsightService.CandidateActionResult} for the new admin surfaces.
 */
public record GovernanceResult(Integer errno, String error, Object data) {

    static GovernanceResult fail(int errno, String message) {
        return new GovernanceResult(errno, message, null);
    }

    static GovernanceResult ok(Object data) {
        return new GovernanceResult(null, null, data);
    }
}
