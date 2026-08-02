package org.linlinjava.litemall.core.mail;

/**
 * Outbound CUSTOMER email (Wave 6) — transactional messages to the buyer
 * (order confirmation, shipped, refund approved, pickup code, password reset).
 *
 * <p>Deliberately separate from {@link org.linlinjava.litemall.core.notify.NotifyService},
 * which mails the OPERATOR (fixed sendto) and stays untouched. Configured via the
 * {@code litemall.customer-mail.*} namespace ({@link CustomerMailProperties});
 * disabled by default, in which case the bound bean is a no-op logger and every
 * core dependent boots unchanged (see {@link CustomerMailAutoConfiguration}).
 */
public interface CustomerMailSender {

    /**
     * Send one plain-text email synchronously.
     *
     * @throws org.springframework.mail.MailException (or any RuntimeException) on
     *         delivery failure — callers like the order outbox sweep rely on the
     *         exception to record the attempt and retry later. Never call this on
     *         a request/payment thread; enqueue and let a sweep deliver.
     */
    void send(String to, String subject, String textBody);

    /**
     * Send one email with an HTML alternative (V48). A null/blank {@code htmlBody}
     * degrades to the plain-text send, so callers can pass the outbox row's
     * {@code body_html} straight through. Same throwing contract as
     * {@link #send(String, String, String)}.
     */
    default void send(String to, String subject, String textBody, String htmlBody) {
        send(to, subject, textBody);
    }
}
