package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.core.mail.CustomerMailProperties;
import org.linlinjava.litemall.core.mail.CustomerMailSender;
import org.linlinjava.litemall.db.dao.MailOutboxMapper;
import org.linlinjava.litemall.db.domain.LitemallMailOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Delivers the {@code litemall_mail_outbox} rows enqueued by
 * {@link CustomerMailEnqueueListener} (Wave 6). Mirrors
 * {@link org.linlinjava.litemall.order.application.internal.cj.CjOrderStatusSyncScheduler}:
 * the source set is read straight off the table ({@code findSendable} — pending AND
 * {@code send_at <= now}, so future-dated rows wait for their time), one row's
 * failure never poisons the sweep, and a raced/failed row is simply picked up
 * again next time.
 *
 * <p>Each failed attempt records {@code last_error} and bumps {@code attempts};
 * at {@value #MAX_ATTEMPTS} attempts the row goes {@code failed} (terminal until
 * an admin resend resets it to pending with attempts zeroed).
 */
@Component
public class MailOutboxSweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(MailOutboxSweepScheduler.class);

    /** Attempt cap: the row is flipped to failed on the 5th failed delivery. */
    static final int MAX_ATTEMPTS = 5;
    /** Column width of litemall_mail_outbox.last_error. */
    private static final int MAX_ERROR_LENGTH = 511;

    private final MailOutboxMapper mailOutboxMapper;
    private final CustomerMailSender mailSender;
    private final CustomerMailProperties mailProperties;

    /** Max rows delivered per sweep — backlog beyond this waits for the next pass. */
    @Value("${litemall.customer-mail.sweep-batch:50}")
    private int batchSize;

    public MailOutboxSweepScheduler(MailOutboxMapper mailOutboxMapper,
                                    CustomerMailSender mailSender,
                                    CustomerMailProperties mailProperties) {
        this.mailOutboxMapper = mailOutboxMapper;
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    @Scheduled(fixedDelayString = "${litemall.customer-mail.sweep-ms:60000}")
    public void sweep() {
        if (!mailProperties.isEnabled()) {
            // Disabled default: the bound sender is the no-op logger — "delivering"
            // through it would mark rows sent without any mail leaving. Skip entirely.
            return;
        }
        List<LitemallMailOutbox> due = mailOutboxMapper.findSendable(LocalDateTime.now(), batchSize);
        if (due == null || due.isEmpty()) {
            return;
        }
        log.info("Mail outbox sweep: {} sendable row(s)", due.size());
        for (LitemallMailOutbox row : due) {
            deliver(row);
        }
    }

    /** Try one row; every failure path ends in a guarded bookkeeping UPDATE, never a throw. */
    private void deliver(LitemallMailOutbox row) {
        try {
            mailSender.send(row.getRecipient(), row.getSubject(), row.getBody());
            mailOutboxMapper.markSent(row.getId(), LocalDateTime.now());
        } catch (RuntimeException e) {
            String error = truncate(e.toString());
            int attemptsAfter = (row.getAttempts() == null ? 0 : row.getAttempts()) + 1;
            try {
                if (attemptsAfter >= MAX_ATTEMPTS) {
                    mailOutboxMapper.markFailed(row.getId(), error, LocalDateTime.now());
                    log.warn("Mail outbox row {} FAILED after {} attempts (to {}, template {}): {} — "
                                    + "resendable via POST /srv/private/admin/mail/{}/resend",
                            row.getId(), attemptsAfter, row.getRecipient(), row.getTemplateKey(), error, row.getId());
                } else {
                    mailOutboxMapper.incrementAttempts(row.getId(), error, LocalDateTime.now());
                    log.warn("Mail outbox row {} delivery attempt {}/{} failed (to {}): {} — will retry next sweep",
                            row.getId(), attemptsAfter, MAX_ATTEMPTS, row.getRecipient(), error);
                }
            } catch (RuntimeException bookkeeping) {
                // Even broken bookkeeping must not abort the batch; the row stays
                // pending and is retried (attempts under-counted, which is safe).
                log.warn("Mail outbox bookkeeping failed for row {}: {}", row.getId(), bookkeeping.toString());
            }
        }
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
