package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjTrackingFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjTrackingSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Auto-confirms shipped orders the customer never confirmed: once an order has been
 * SHIPPED for longer than the grace window it transitions to AUTO_DELIVERED, closing
 * the lifecycle without manual action. Mirrors {@link UnpaidOrderTaskScheduler}, but
 * the source set is read straight off {@code litemall_order} (SHIPPED + old ship_time)
 * via {@link LitemallOrderRepository#queryUnconfirm(int)} — no separate task table.
 *
 * <p>Each order is confirmed in its OWN transaction (REQUIRES_NEW inside
 * {@link LitemallOrderServiceImpl#autoConfirmOrder}), so one failure does not poison
 * the sweep. The guarded UPDATE makes the confirm a no-op if the customer confirmed
 * first (a race the sweep simply skips).
 *
 * <p><b>CJ orders close on the carrier's word, not on the calendar</b> (2026-11-21). The
 * grace window counts from the ship time, and a parcel from China is routinely still in
 * transit when it runs out: prod order 11 was closed as delivered — mail sent, return
 * window started — while it was clearing customs. A CJ-fulfilled order past the window is
 * therefore confirmed only when CJ's tracking says it was delivered. Without that word
 * (still in transit, no tracking number, CJ unreachable) it stays SHIPPED and is asked
 * again next sweep, until {@code litemall.order.cj.auto-confirm-max-days} after shipping,
 * when it closes anyway so no order stays open forever. Local orders are unchanged.
 */
@Component
public class AutoConfirmOrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoConfirmOrderScheduler.class);

    private final LitemallOrderRepository orderRepository;
    private final LitemallOrderServiceImpl orderServiceImpl;
    /** Null in hand-built tests: a CJ order then simply waits for the cap. */
    private final CjTrackingFacade trackingFacade;

    /**
     * Days after shipping at which a CJ order closes even without a delivery report. A value
     * at or below the grace window (e.g. 0) turns the delivery check off: every order then
     * closes on the window alone, as before 2026-11-21.
     */
    @Value("${litemall.order.cj.auto-confirm-max-days:45}")
    private int cjMaxDays = 45;

    /**
     * Fallback only. The admin-editable {@code litemall_order_unconfirm} system setting
     * (litemall_system, exposed in the admin panel like the unpaid window) is the source of
     * truth when it is set; until 2026-09-05 it was silently ignored while this code default
     * (15) ran and the panel said 7 (plan-order-lifecycle-e2e.md, F14).
     */
    @Value("${litemall.order.auto-confirm-days:15}")
    private int autoConfirmDaysFallback;

    /** The effective grace window: system setting when positive, else the yml/code fallback. */
    int autoConfirmDays() {
        Integer configured = null;
        try {
            configured = org.linlinjava.litemall.core.system.SystemConfig.getOrderUnconfirm();
        } catch (RuntimeException ignored) {
            // SystemConfig not loaded (tests, early boot): fall back
        }
        return configured != null && configured > 0 ? configured : Math.max(1, autoConfirmDaysFallback);
    }

    public AutoConfirmOrderScheduler(LitemallOrderRepository orderRepository,
                                     LitemallOrderServiceImpl orderServiceImpl) {
        this(orderRepository, orderServiceImpl, null);
    }

    @Autowired
    public AutoConfirmOrderScheduler(LitemallOrderRepository orderRepository,
                                     LitemallOrderServiceImpl orderServiceImpl,
                                     CjTrackingFacade trackingFacade) {
        this.orderRepository = orderRepository;
        this.orderServiceImpl = orderServiceImpl;
        this.trackingFacade = trackingFacade;
    }

    @Scheduled(fixedDelayString = "${litemall.order.auto-confirm-sweep-ms:3600000}")
    public void sweep() {
        int autoConfirmDays = autoConfirmDays();
        List<LitemallOrderAggregate> due = orderRepository.queryUnconfirm(autoConfirmDays);
        if (due == null || due.isEmpty()) {
            return;
        }
        log.info("Auto-confirm sweep: {} shipped orders past the {}-day window", due.size(), autoConfirmDays);
        for (LitemallOrderAggregate order : due) {
            try {
                if (!mayClose(order, autoConfirmDays)) {
                    continue;
                }
                orderServiceImpl.autoConfirmOrder(order.getOrderId());
            } catch (RuntimeException e) {
                log.warn("Failed to auto-confirm order {}; will retry next sweep",
                        order.getOrderId().getId(), e);
            }
        }
    }

    /** Local orders close on the window; CJ orders on a delivery report or the cap. */
    private boolean mayClose(LitemallOrderAggregate order, int windowDays) {
        if (!order.isCjFulfilled() || cjMaxDays <= windowDays) {
            return true;
        }
        Integer id = order.getOrderId().getId();
        if (deliveredAccordingToCj(order)) {
            log.info("Auto-confirming CJ order {}: CJ tracking reports it delivered", id);
            return true;
        }
        LocalDateTime shipped = order.getShipTime();
        if (shipped != null && shipped.isBefore(LocalDateTime.now().minusDays(cjMaxDays))) {
            log.warn("Auto-confirming CJ order {} WITHOUT a delivery report: shipped {} days ago or more "
                    + "(litemall.order.cj.auto-confirm-max-days)", id, cjMaxDays);
            return true;
        }
        log.info("CJ order {} is past the {}-day window but CJ does not report it delivered; left SHIPPED",
                id, windowDays);
        return false;
    }

    private boolean deliveredAccordingToCj(LitemallOrderAggregate order) {
        String trackNumber = order.getShipSn();
        if (trackingFacade == null || trackNumber == null || trackNumber.isBlank()) {
            return false;
        }
        // Never throws (the facade answers empty on any CJ failure) and is cached for an hour.
        return trackingFacade.trackInfo(trackNumber).map(CjTrackingSnapshot::isDelivered).orElse(false);
    }
}
