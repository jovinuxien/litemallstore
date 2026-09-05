package org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj;

/**
 * The answer to a best-effort CJ mutation (confirm / pay-from-balance) WITH the reason
 * when it failed. The boolean forms of these calls swallowed CJ's message into a WARN
 * log, which is how "insufficient balance" retried every five minutes for weeks with
 * nobody told (plan-order-lifecycle-e2e.md, F5). {@code message} is operator-facing.
 */
public record CjCallOutcome(boolean ok, String message) {

    public static CjCallOutcome accepted() {
        return new CjCallOutcome(true, null);
    }

    public static CjCallOutcome rejected(String message) {
        return new CjCallOutcome(false, message == null || message.isBlank() ? "no reason given" : message);
    }
}
