package org.linlinjava.litemall.core.mail;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * SMTP implementation of {@link CustomerMailSender} over Spring's
 * {@link JavaMailSenderImpl}, built entirely from {@link CustomerMailProperties}
 * (no dependency on {@code spring.mail.*}). SMTP auth is engaged only when a
 * username is configured — MailHog (the dev default) authenticates nobody.
 *
 * <p>Bound by {@link CustomerMailAutoConfiguration} only when
 * {@code litemall.customer-mail.enabled=true}.
 */
public class SmtpCustomerMailSender implements CustomerMailSender {

    private final JavaMailSenderImpl mailSender;
    private final String from;

    public SmtpCustomerMailSender(CustomerMailProperties config) {
        this.from = config.getFrom();
        this.mailSender = new JavaMailSenderImpl();
        mailSender.setHost(config.getHost());
        mailSender.setPort(config.getPort());
        Properties props = new Properties();
        if (config.getUsername() != null && !config.getUsername().isBlank()) {
            mailSender.setUsername(config.getUsername());
            mailSender.setPassword(config.getPassword());
            props.put("mail.smtp.auth", true);
            props.put("mail.smtp.starttls.enable", true);
        }
        // Fail fast when SMTP is down: the outbox sweep must record the failure and
        // move on, not hang its scheduler thread on the JavaMail infinite default.
        props.put("mail.smtp.connectiontimeout", 5000);
        props.put("mail.smtp.timeout", 5000);
        props.put("mail.smtp.writetimeout", 5000);
        mailSender.setJavaMailProperties(props);
    }

    @Override
    public void send(String to, String subject, String textBody) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(textBody);
        mailSender.send(message); // MailException propagates to the caller
    }
}
