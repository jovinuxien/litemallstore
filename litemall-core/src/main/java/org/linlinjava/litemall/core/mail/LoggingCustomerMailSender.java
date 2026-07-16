package org.linlinjava.litemall.core.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Disabled-mode {@link CustomerMailSender}: logs and drops. The default bean when
 * {@code litemall.customer-mail.enabled=false} (which is the shipped default), so
 * every core dependent keeps booting with zero SMTP configuration.
 *
 * <p>Note: order's enqueue listeners and outbox sweep check the enabled flag
 * themselves and never reach this sender — it exists so injection points always
 * have a bean and so stray callers are visible in the log instead of failing.
 */
public class LoggingCustomerMailSender implements CustomerMailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingCustomerMailSender.class);

    @Override
    public void send(String to, String subject, String textBody) {
        log.info("customer-mail disabled (litemall.customer-mail.enabled=false); dropping mail to {}: {}",
                to, subject);
    }
}
