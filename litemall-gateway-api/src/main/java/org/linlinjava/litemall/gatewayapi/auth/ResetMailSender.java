package org.linlinjava.litemall.gatewayapi.auth;

/**
 * Port for delivering password-reset tokens by email.
 *
 * <p>litemall-core's NotifyService has a FIXED sendTo (ops mailbox) and cannot
 * mail customers, so the edge defines its own seam. Default binding is the
 * no-op {@link LoggingResetMailSender}; a real SMTP/provider implementation
 * replaces it by contributing another bean.
 *
 * <p>Callers fire-and-forget: a send failure must never surface to the
 * requester (anti-enumeration — the response is errno 0 either way).
 */
public interface ResetMailSender {

    /**
     * Deliver the RAW reset token to the address. The token is shown once and
     * never stored server-side (only its SHA-256 hash is persisted).
     */
    void send(String email, String rawToken);
}
