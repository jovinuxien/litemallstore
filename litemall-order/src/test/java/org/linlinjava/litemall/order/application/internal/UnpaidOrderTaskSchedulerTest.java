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

class UnpaidOrderTaskSchedulerTest {

    @Test
    void schedule_persistsTaskWithComputedDueAt() {
        LitemallUnpaidOrderTaskRepository repo = mock(LitemallUnpaidOrderTaskRepository.class);
        LitemallOrderServiceImpl orderService = mock(LitemallOrderServiceImpl.class);
        UnpaidOrderTaskScheduler scheduler = new UnpaidOrderTaskScheduler(repo, orderService);

        LocalDateTime dueAt = LocalDateTime.now().plusMinutes(30);
        scheduler.schedule(new LitemallOrderId(42), dueAt);

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
        LitemallUnpaidOrderTaskRepository repo = mock(LitemallUnpaidOrderTaskRepository.class);
        LitemallOrderServiceImpl orderService = mock(LitemallOrderServiceImpl.class);
        UnpaidOrderTaskScheduler scheduler = new UnpaidOrderTaskScheduler(repo, orderService);

        scheduler.cancel(new LitemallOrderId(42));

        verify(repo).deleteByOrderId(argThat(id -> id.getId().equals(42)));
    }

    @Test
    void sweep_cancelsEveryDueOrderAndDeletesRow() {
        LitemallUnpaidOrderTaskRepository repo = mock(LitemallUnpaidOrderTaskRepository.class);
        LitemallOrderServiceImpl orderService = mock(LitemallOrderServiceImpl.class);
        LitemallUnpaidOrderTaskAggregate task1 = task(101);
        LitemallUnpaidOrderTaskAggregate task2 = task(102);
        when(repo.findDue(any(LocalDateTime.class), anyInt())).thenReturn(List.of(task1, task2));

        UnpaidOrderTaskScheduler scheduler = new UnpaidOrderTaskScheduler(repo, orderService);
        scheduler.sweep();

        verify(orderService).cancelOrder(argThat(id -> id.getId().equals(101)), anyString());
        verify(orderService).cancelOrder(argThat(id -> id.getId().equals(102)), anyString());
        verify(repo).deleteByOrderId(argThat(id -> id.getId().equals(101)));
        verify(repo).deleteByOrderId(argThat(id -> id.getId().equals(102)));
    }

    @Test
    void sweep_leavesRowForRetryWhenCancelThrows() {
        LitemallUnpaidOrderTaskRepository repo = mock(LitemallUnpaidOrderTaskRepository.class);
        LitemallOrderServiceImpl orderService = mock(LitemallOrderServiceImpl.class);
        when(repo.findDue(any(LocalDateTime.class), anyInt())).thenReturn(List.of(task(500)));
        doThrow(new RuntimeException("boom")).when(orderService).cancelOrder(any(), anyString());

        UnpaidOrderTaskScheduler scheduler = new UnpaidOrderTaskScheduler(repo, orderService);
        scheduler.sweep();

        verify(repo, never()).deleteByOrderId(any());
    }

    @Test
    void sweep_noopOnEmpty() {
        LitemallUnpaidOrderTaskRepository repo = mock(LitemallUnpaidOrderTaskRepository.class);
        LitemallOrderServiceImpl orderService = mock(LitemallOrderServiceImpl.class);
        when(repo.findDue(any(LocalDateTime.class), anyInt())).thenReturn(List.of());

        new UnpaidOrderTaskScheduler(repo, orderService).sweep();

        verifyNoInteractions(orderService);
        verify(repo, never()).deleteByOrderId(any());
    }

    private static LitemallUnpaidOrderTaskAggregate task(int orderId) {
        LitemallUnpaidOrderTaskAggregate t = new LitemallUnpaidOrderTaskAggregate();
        t.setOrderId(new LitemallOrderId(orderId));
        t.setDueAt(LocalDateTime.now().minusMinutes(1));
        t.setCreatedAt(LocalDateTime.now().minusMinutes(31));
        return t;
    }
}
