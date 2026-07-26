package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.core.mail.CustomerMailProperties;
import org.linlinjava.litemall.core.mail.CustomerMailSender;
import org.linlinjava.litemall.db.dao.MailOutboxMapper;
import org.linlinjava.litemall.db.domain.LitemallMailOutbox;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Wave-10 coverage for the outbox delivery sweep: sent, retry, terminal-fail, disabled. */
class MailOutboxSweepSchedulerTest {

    private final MailOutboxMapper mailOutboxMapper = mock(MailOutboxMapper.class);
    private final CustomerMailSender mailSender = mock(CustomerMailSender.class);

    private MailOutboxSweepScheduler scheduler(boolean enabled) {
        CustomerMailProperties properties = new CustomerMailProperties();
        properties.setEnabled(enabled);
        return new MailOutboxSweepScheduler(mailOutboxMapper, mailSender, properties);
    }

    private static LitemallMailOutbox row(int id, int attempts) {
        LitemallMailOutbox row = new LitemallMailOutbox();
        row.setId(id);
        row.setRecipient("buyer@example.com");
        row.setSubject("subject");
        row.setBody("body");
        row.setTemplateKey("order-confirmation");
        row.setStatus(LitemallMailOutbox.STATUS_PENDING);
        row.setAttempts(attempts);
        return row;
    }

    @Test
    void sweep_deliversAndMarksSent() {
        when(mailOutboxMapper.findSendable(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(row(1, 0)));

        scheduler(true).sweep();

        verify(mailSender).send("buyer@example.com", "subject", "body");
        verify(mailOutboxMapper).markSent(eq(1), any(LocalDateTime.class));
        verify(mailOutboxMapper, never()).incrementAttempts(any(), any(), any());
        verify(mailOutboxMapper, never()).markFailed(any(), any(), any());
    }

    @Test
    void sweep_failureIncrementsAttemptsForRetry() {
        when(mailOutboxMapper.findSendable(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(row(1, 0)));
        doThrow(new RuntimeException("smtp down")).when(mailSender).send(any(), any(), any());

        scheduler(true).sweep();

        verify(mailOutboxMapper).incrementAttempts(eq(1), contains("smtp down"), any(LocalDateTime.class));
        verify(mailOutboxMapper, never()).markSent(any(), any());
        verify(mailOutboxMapper, never()).markFailed(any(), any(), any());
    }

    @Test
    void sweep_fifthFailureIsTerminal() {
        when(mailOutboxMapper.findSendable(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(row(1, MailOutboxSweepScheduler.MAX_ATTEMPTS - 1)));
        doThrow(new RuntimeException("smtp down")).when(mailSender).send(any(), any(), any());

        scheduler(true).sweep();

        verify(mailOutboxMapper).markFailed(eq(1), contains("smtp down"), any(LocalDateTime.class));
        verify(mailOutboxMapper, never()).incrementAttempts(any(), any(), any());
    }

    @Test
    void sweep_oneRowsFailureNeverPoisonsTheBatch() {
        LitemallMailOutbox second = row(2, 0);
        second.setRecipient("other@example.com");
        when(mailOutboxMapper.findSendable(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(row(1, 0), second));
        doThrow(new RuntimeException("smtp down")).when(mailSender).send(eq("buyer@example.com"), any(), any());
        // even the bookkeeping UPDATE blowing up must not abort the sweep
        doThrow(new RuntimeException("db down")).when(mailOutboxMapper).incrementAttempts(any(), any(), any());

        scheduler(true).sweep();

        verify(mailSender).send(eq("other@example.com"), any(), any());
        verify(mailOutboxMapper).markSent(eq(2), any(LocalDateTime.class));
    }

    @Test
    void sweep_disabledTouchesNothing() {
        scheduler(false).sweep();

        verifyZeroInteractions(mailOutboxMapper, mailSender);
    }
}
