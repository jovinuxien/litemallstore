package org.linlinjava.litemall.gatewayapi.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Real {@link ResetMailSender} over SMTP (Wave-6 Task B).
 *
 * <p>Reads the SAME {@code litemall.customer-mail.*} namespace as
 * litemall-core's CustomerMailSender (order's transactional outbox) so one set
 * of ENV vars configures customer mail everywhere — but this is a deliberate
 * edge-local implementation with its OWN JavaMailSender: the reactive gateway
 * takes no code dependency on litemall-core (which drags in servlet MVC).
 *
 * <p>Gating is two-layered and unchanged from V37:
 * <ul>
 *   <li>{@code litemall.auth.reset-mail.enabled} (outer) — the flow itself;
 *       disabled → /auth/reset/request answers 701 and no send is attempted.</li>
 *   <li>{@code litemall.customer-mail.enabled} (this bean) — false/absent →
 *       this configuration contributes nothing and the dev no-op
 *       {@link LoggingResetMailSender} stays bound.</li>
 * </ul>
 *
 * <p>Anti-enumeration semantics live in the caller ({@code AccountService}
 * fire-and-forgets and swallows send failures); this sender just throws on
 * transport errors. Template key {@code password-reset} per the Wave-6 shared
 * contract (plain-text English v1, mirroring core's MailTemplates).
 */
@Configuration
@ConditionalOnProperty(name = "litemall.customer-mail.enabled", havingValue = "true")
public class SmtpResetMailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpResetMailSender.class);

    // NB: method name must differ from the @Configuration class name — the
    // class itself is registered under 'smtpResetMailSender'.
    @Bean
    public ResetMailSender smtpBackedResetMailSender(
            @Value("${litemall.customer-mail.host}") String host,
            @Value("${litemall.customer-mail.port:25}") int port,
            @Value("${litemall.customer-mail.user:}") String user,
            @Value("${litemall.customer-mail.pass:}") String pass,
            @Value("${litemall.customer-mail.from}") String from) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        if (!user.isBlank()) {
            sender.setUsername(user);
            sender.setPassword(pass);
            sender.getJavaMailProperties().setProperty("mail.smtp.auth", "true");
            sender.getJavaMailProperties().setProperty("mail.smtp.starttls.enable", "true");
        }
        log.info("SMTP ResetMailSender active (host={}, port={}, from={})", host, port, from);
        return (email, rawToken) -> {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(email);
            // Template key: password-reset (Wave-6 shared template contract).
            msg.setSubject("Your litemall password reset code");
            msg.setText("Hello,\n\n"
                    + "We received a request to reset the password of the litemall account "
                    + "registered to this address.\n\n"
                    + "Your reset code (valid for 30 minutes, single use):\n\n"
                    + "    " + rawToken + "\n\n"
                    + "Enter it on the \"Forgot password\" tab of the password page to choose "
                    + "a new password.\n\n"
                    + "If you did not request this, you can safely ignore this email — your "
                    + "password is unchanged.\n");
            sender.send(msg);
            log.info("password-reset mail sent to {}", email);
        };
    }
}
