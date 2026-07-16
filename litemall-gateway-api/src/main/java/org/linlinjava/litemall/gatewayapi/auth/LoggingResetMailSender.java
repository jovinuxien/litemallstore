package org.linlinjava.litemall.gatewayapi.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default no-op {@link ResetMailSender}: logs the send request (dev aid) and
 * delivers nothing. The email reset flow ships disabled
 * ({@code litemall.auth.reset-mail.enabled:false}); this default exists so
 * enabling the flag in a dev environment is verifiable without SMTP.
 *
 * <p>SECURITY: the raw token IS logged here. That is acceptable only because
 * this bean is the dev placeholder — a production deployment enabling
 * reset-mail must contribute a real sender bean, which overrides this one via
 * {@link ConditionalOnMissingBean}.
 */
@Configuration
public class LoggingResetMailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingResetMailSender.class);

    // The property condition (not just missing-bean) makes the choice between
    // this and SmtpResetMailSender deterministic regardless of configuration-
    // class processing order: exactly one of the two matches.
    @Bean
    @ConditionalOnMissingBean(ResetMailSender.class)
    @ConditionalOnProperty(name = "litemall.customer-mail.enabled", havingValue = "false", matchIfMissing = true)
    public ResetMailSender loggingOnlyResetMailSender() {
        return (email, rawToken) -> log.warn(
                "[dev no-op ResetMailSender] password-reset token for {} (NOT delivered): {}",
                email, rawToken);
    }
}
