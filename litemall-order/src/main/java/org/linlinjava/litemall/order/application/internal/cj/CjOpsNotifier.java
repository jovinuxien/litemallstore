package org.linlinjava.litemall.order.application.internal.cj;

import org.linlinjava.litemall.core.mail.CustomerMailProperties;
import org.linlinjava.litemall.db.dao.MailOutboxMapper;
import org.linlinjava.litemall.db.domain.LitemallMailOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * Ops attention signal for CJ fulfilment anomalies (Wave 8): terminal placement rejection of
 * a paid order, CJ-side cancellation of a placed order. Best-effort mail through the Wave-6
 * outbox ({@code litemall_mail_outbox}, delivered by {@code MailOutboxSweepScheduler}) to the
 * {@code litemall.order.cj-ops-mail} address. Blank address or mail seam disabled ⇒ the
 * caller's ERROR/WARN log is the signal; enqueue failures never break the calling flow.
 */
@Component
public class CjOpsNotifier {

    private static final Logger log = LoggerFactory.getLogger(CjOpsNotifier.class);

    private final MailOutboxMapper mailOutboxMapper;
    private final CustomerMailProperties mailProperties;
    /** Ops mailbox for fulfilment-attention signals; blank = log-only. */
    private final String opsMail;

    public CjOpsNotifier(MailOutboxMapper mailOutboxMapper,
                         CustomerMailProperties mailProperties,
                         @Value("${litemall.order.cj-ops-mail:}") String opsMail) {
        this.mailOutboxMapper = mailOutboxMapper;
        this.mailProperties = mailProperties;
        this.opsMail = opsMail;
    }

    public void notify(String subject, String body) {
        if (!StringUtils.hasText(opsMail)) {
            log.warn("CJ ops mail not configured (litemall.order.cj-ops-mail) — '{}' is log-only", subject);
            return;
        }
        if (!mailProperties.isEnabled()) {
            log.warn("customer-mail seam disabled — CJ ops mail '{}' to {} is log-only", subject, opsMail);
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            LitemallMailOutbox row = new LitemallMailOutbox();
            row.setRecipient(opsMail.trim());
            row.setSubject(subject);
            row.setBody(body);
            row.setTemplateKey("cj_ops_alert");
            row.setStatus(LitemallMailOutbox.STATUS_PENDING);
            row.setAttempts(0);
            row.setSendAt(now);
            row.setAddTime(now);
            row.setUpdateTime(now);
            row.setDeleted(false);
            mailOutboxMapper.insert(row);
        } catch (RuntimeException e) {
            log.warn("could not enqueue CJ ops mail '{}': {}", subject, e.getMessage());
        }
    }
}
