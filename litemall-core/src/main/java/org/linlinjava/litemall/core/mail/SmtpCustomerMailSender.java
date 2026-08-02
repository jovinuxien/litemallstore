package org.linlinjava.litemall.core.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.util.Properties;

/**
 * SMTP implementation of {@link CustomerMailSender} over Spring's
 * {@link JavaMailSenderImpl}, built entirely from {@link CustomerMailProperties}
 * (no dependency on {@code spring.mail.*}). SMTP auth is engaged only when a
 * username is configured — MailHog (the dev default) authenticates nobody.
 *
 * <p>Plain-text rows go out as {@link SimpleMailMessage}; rows carrying a
 * {@code body_html} (V48) go out as multipart/alternative via
 * {@link MimeMessageHelper} — HTML with the plain text as the fallback part
 * (spring-boot-starter-mail is a compile-scope core dependency, so the
 * jakarta.mail implementation is always on the classpath).
 *
 * <p>Bound by {@link CustomerMailAutoConfiguration} only when
 * {@code litemall.customer-mail.enabled=true}.
 */
public class SmtpCustomerMailSender implements CustomerMailSender {

    private final JavaMailSenderImpl mailSender;
    private final String from;

    public SmtpCustomerMailSender(CustomerMailProperties config) {
        this.from = config.getFrom();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getHost());
        sender.setPort(config.getPort());
        Properties props = new Properties();
        if (config.getUsername() != null && !config.getUsername().isBlank()) {
            sender.setUsername(config.getUsername());
            sender.setPassword(config.getPassword());
            props.put("mail.smtp.auth", true);
            props.put("mail.smtp.starttls.enable", true);
        }
        // Fail fast when SMTP is down: the outbox sweep must record the failure and
        // move on, not hang its scheduler thread on the JavaMail infinite default.
        props.put("mail.smtp.connectiontimeout", 5000);
        props.put("mail.smtp.timeout", 5000);
        props.put("mail.smtp.writetimeout", 5000);
        sender.setJavaMailProperties(props);
        this.mailSender = sender;
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

    @Override
    public void send(String to, String subject, String textBody, String htmlBody) {
        if (htmlBody == null || htmlBody.isBlank()) {
            send(to, subject, textBody);
            return;
        }
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textBody == null ? "" : textBody, htmlBody);
        } catch (MessagingException e) {
            // Malformed message construction is a permanent error for this row: the
            // sweep records it like any delivery failure and retries up to the cap.
            throw new MailPreparationException("Failed to build multipart mail", e);
        }
        mailSender.send(message); // MailException propagates to the caller
    }
}
