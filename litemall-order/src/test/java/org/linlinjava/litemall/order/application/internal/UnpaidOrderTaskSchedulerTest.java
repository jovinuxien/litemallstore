package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallUnpaidOrderTaskAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallUnpaidOrderTaskRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * The sweep is now a thin loop over {@link UnpaidOrderReconciler}: it owns the task rows
 * (claim, delete, defer) and nothing else. What happens to the ORDER is the reconciler's
 * business and is tested there.
 */
class UnpaidOrderTaskSchedulerTest {

    private final LitemallUnpaidOrderTaskRepository repo = mock(LitemallUnpaidOrderTaskRepository.class);
    private final UnpaidOrderReconciler reconciler = mock(UnpaidOrderReconciler.class);

    private UnpaidOrderTaskScheduler scheduler() {
        return new UnpaidOrderTaskScheduler(repo, reconciler);
    }

    @Test
    void schedule_persistsTaskWithComputedDueAt() {
        LocalDateTime dueAt = LocalDateTime.now().plusMinutes(30);
        scheduler().schedule(new LitemallOrderId(42), dueAt);

        ArgumentCaptor<LitemallUnpaidOrderTaskAggregate> captor =
                ArgumentCaptor.forClass(LitemallUnpaidOrderTaskAggregate.class);
        verify(repo).upsert(captor.capture());
        LitemallUnpaidOrderTaskAggregate saved = captor.getValue();
        assertThat(saved.getOrderId().getId()).isEqualTo(42);
        assertThat(saved.getDueAt()).isEqualTo(dueAt);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void cancel_deletesRow() {
        scheduler().cancel(new LitemallOrderId(42));

        verify(repo).deleteByOrderId(argThat(id -> id.getId().equals(42)));
    }

    @Test
    void sweep_resolvesEveryDueOrderAndDeletesRowOnCancelledOrPaid() {
        when(repo.claimDueBatch(any(LocalDateTime.class), anyInt())).thenReturn(List.of(task(101), task(102)));
        when(reconciler.resolve(argThat(id -> id != null && id.getId().equals(101)))).thenReturn(outcome("cancelled"));
        when(reconciler.resolve(argThat(id -> id != null && id.getId().equals(102)))).thenReturn(outcome("paid"));

        scheduler().sweep();

        verify(repo).deleteByOrderId(argThat(id -> id.getId().equals(101)));
        verify(repo).deleteByOrderId(argThat(id -> id.getId().equals(102)));
        verify(repo, never()).upsert(any());
    }

    /**
     * Money in flight (SEPA) or an unreachable PSP: the row is kept and pushed out to the
     * reconciler's deferral time — the order is NOT cancelled and the task is NOT retired.
     */
    @Test
    void sweep_defersTheRowInsteadOfDeletingItWhenTheReconcilerSaysSo() {
        LocalDateTime later = LocalDateTime.now().plusMinutes(60);
        when(repo.claimDueBatch(any(LocalDateTime.class), anyInt())).thenReturn(List.of(task(7)));
        when(reconciler.resolve(any())).thenReturn(deferred(later));

        scheduler().sweep();

        ArgumentCaptor<LitemallUnpaidOrderTaskAggregate> captor =
                ArgumentCaptor.forClass(LitemallUnpaidOrderTaskAggregate.class);
        verify(repo).upsert(captor.capture());
        assertThat(captor.getValue().getOrderId().getId()).isEqualTo(7);
        assertThat(captor.getValue().getDueAt()).isEqualTo(later);
        verify(repo, never()).deleteByOrderId(any());
    }

    @Test
    void sweep_leavesRowForRetryWhenResolveThrows() {
        when(repo.claimDueBatch(any(LocalDateTime.class), anyInt())).thenReturn(List.of(task(500)));
        when(reconciler.resolve(any())).thenThrow(new RuntimeException("boom"));

        scheduler().sweep();

        verify(repo, never()).deleteByOrderId(any());
        verify(repo, never()).upsert(any());
    }

    /**
     * A task whose order no longer exists can never succeed. Retrying it forever is
     * the loop the August fix closed, so the row is retired instead (INFO, not WARN).
     */
    @Test
    void sweep_dropsTheTaskWhenTheOrderIsGone() {
        when(repo.claimDueBatch(any(LocalDateTime.class), anyInt())).thenReturn(List.of(task(8)));
        when(reconciler.resolve(any())).thenThrow(new java.util.NoSuchElementException("Order not found"));

        scheduler().sweep();

        verify(repo).deleteByOrderId(argThat(id -> id.getId().equals(8)));
    }

    @Test
    void sweep_noopOnEmpty() {
        when(repo.claimDueBatch(any(LocalDateTime.class), anyInt())).thenReturn(List.of());

        scheduler().sweep();

        verifyNoMoreInteractions(reconciler);
        verify(repo, never()).deleteByOrderId(any());
    }

    private static LitemallUnpaidOrderTaskAggregate task(int orderId) {
        LitemallUnpaidOrderTaskAggregate t = new LitemallUnpaidOrderTaskAggregate();
        t.setOrderId(new LitemallOrderId(orderId));
        t.setDueAt(LocalDateTime.now().minusMinutes(1));
        t.setCreatedAt(LocalDateTime.now().minusMinutes(31));
        return t;
    }

    /** The reconciler's factories are package-private; reach them through the real class. */
    private static UnpaidOrderReconciler.Outcome outcome(String kind) {
        return switch (kind) {
            case "cancelled" -> UnpaidOrderReconciler.Outcome.cancelled();
            case "paid" -> UnpaidOrderReconciler.Outcome.paid();
            default -> UnpaidOrderReconciler.Outcome.retired();
        };
    }

    private static UnpaidOrderReconciler.Outcome deferred(LocalDateTime until) {
        return UnpaidOrderReconciler.Outcome.deferred(until);
    }
}
