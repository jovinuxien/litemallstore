package org.linlinjava.litemall.order.application.internal.cj;

import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.events.cj.LitemallCjFulfilmentCancelledEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything that happens when CJ fulfilment goes WRONG for a paid order, in one place
 * (plan-order-lifecycle-e2e.md, package B: F5, F6, F8). Before this class every one of
 * these was a WARN line: a placement failing every five minutes forever, a CJ order never
 * paid from balance, a CJ-side cancellation the customer never heard about.
 *
 * <p>State lives in the order's own timeline ({@code litemall_order_status}) — no schema
 * change: "how long has placement been failing" is the first deferred hop since the last
 * requeue/approval, "did we already warn" is the presence of the warning hop. Every
 * method is transactional so the hop, the projection write and the ops/customer signal
 * commit together (the AFTER_COMMIT mail listener sees nothing from a non-transactional
 * caller — the shipped-mail lesson).
 *
 * <p>Money never moves here. Ops mail goes through {@link CjOpsNotifier} (log-only until
 * {@code CJ_OPS_MAIL} is set — decision D6 sets it in prod).
 */
@Service
@Transactional
public class CjFulfilmentIncidentService {

    private static final Logger log = LoggerFactory.getLogger(CjFulfilmentIncidentService.class);

    /** Local sentinel: placement kept failing for {@code stall-park-hours}; requeue clears it. */
    public static final String STATUS_PLACEMENT_STALLED = "PLACEMENT_STALLED";
    /** Timeline change type for the escalation hops (warning / lifecycle failures). */
    public static final String CHANGE_TYPE_CJ_STALL = "cj_stall";

    static final String MSG_DEFERRED = "Fulfilment placement deferred — will be retried automatically";
    static final String MSG_REQUEUED_PREFIX = "Requeued for CJ placement";
    static final String MSG_APPROVED_PREFIX = "Approved for CJ fulfilment";
    static final String MSG_WARNING_PREFIX = "Fulfilment placement has been failing since ";

    private final LitemallOrderRepository orderRepository;
    private final LitemallOrderStatusHistoryRepository statusHistoryRepository;
    private final LitemallDomainEventPublisher domainEventPublisher;
    private final CjOpsNotifier opsNotifier;
    private final Duration warnAfter;
    private final Duration parkAfter;
    private final Duration lifecycleAlertEvery;

    public CjFulfilmentIncidentService(LitemallOrderRepository orderRepository,
                                       LitemallOrderStatusHistoryRepository statusHistoryRepository,
                                       LitemallDomainEventPublisher domainEventPublisher,
                                       CjOpsNotifier opsNotifier,
                                       @Value("${litemall.order.cj.stall-warn-minutes:60}") int stallWarnMinutes,
                                       @Value("${litemall.order.cj.stall-park-hours:24}") int stallParkHours,
                                       @Value("${litemall.order.cj.lifecycle-alert-hours:24}") int lifecycleAlertHours) {
        this.orderRepository = orderRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.opsNotifier = opsNotifier;
        this.warnAfter = Duration.ofMinutes(Math.max(1, stallWarnMinutes));
        this.parkAfter = Duration.ofHours(Math.max(1, stallParkHours));
        this.lifecycleAlertEvery = Duration.ofHours(Math.max(1, lifecycleAlertHours));
    }

    // ------------------------------------------------------------------
    // Placement keeps failing (retryable)
    // ------------------------------------------------------------------

    /** What the placement service should do next after a retryable failure. */
    public enum StallVerdict { RECORDED_FIRST_FAILURE, RETRYING, WARNED, PARKED }

    /**
     * Called on EVERY retryable placement failure (fast path or sweep). Writes one
     * customer-visible "deferred" hop the first time (whichever path hits it first — the
     * old fast-path-only hop left sweep-first failures invisible), an ops warning once
     * after {@code warnAfter}, and parks the order under {@link #STATUS_PLACEMENT_STALLED}
     * after {@code parkAfter} so it stops burning CJ calls and shows up in the admin
     * attention list with a Requeue button.
     */
    public StallVerdict onRetryablePlacementFailure(LitemallOrderAggregate order, String lastError) {
        LitemallOrderId orderId = order.getOrderId();
        List<LitemallOrderStatusChange> history = statusHistoryRepository.findByOrderId(orderId);
        LocalDateTime failingSince = null;
        boolean warned = false;
        for (LitemallOrderStatusChange hop : history) {
            String msg = hop.getChangeMessage() == null ? "" : hop.getChangeMessage();
            if (msg.startsWith(MSG_REQUEUED_PREFIX) || msg.startsWith(MSG_APPROVED_PREFIX)) {
                failingSince = null; // a human reset the clock
                warned = false;
            } else if (msg.equals(MSG_DEFERRED) && failingSince == null) {
                failingSince = hop.getChangeTime();
            } else if (msg.startsWith(MSG_WARNING_PREFIX) && failingSince != null) {
                warned = true;
            }
        }
        LocalDateTime now = LocalDateTime.now();
        if (failingSince == null) {
            hop(order, CjPlacementService.CHANGE_TYPE_CJ_PLACEMENT, MSG_DEFERRED, "system", now);
            return StallVerdict.RECORDED_FIRST_FAILURE;
        }
        Duration failing = Duration.between(failingSince, now);
        if (failing.compareTo(parkAfter) >= 0) {
            orderRepository.updateCjOrderStatus(orderId, STATUS_PLACEMENT_STALLED);
            hop(order, CjPlacementService.CHANGE_TYPE_CJ_PLACEMENT_FAILED,
                    "Fulfilment placement parked after " + failing.toHours() + " h of failed attempts (since "
                    + failingSince + "); last error: " + lastError, "system", now);
            opsNotifier.notify("CJ placement STALLED — order " + order.getOrderSn(),
                    "Placement of PAID order " + orderId.getId() + " (sn " + order.getOrderSn()
                    + ") has failed with retryable errors since " + failingSince + " and was PARKED after "
                    + failing.toHours() + " h.\nLast error: " + lastError
                    + "\n\nThe customer's payment is NOT touched. Fix the cause (CJ credentials, CJ outage, "
                    + "account state), then Requeue the order in the admin panel (Orders → Pending CJ approval) "
                    + "— or refund through the normal refund path.");
            log.error("CJ placement STALLED for order {}: parked after {} h (since {}); last error: {}",
                    orderId.getId(), failing.toHours(), failingSince, lastError);
            return StallVerdict.PARKED;
        }
        if (!warned && failing.compareTo(warnAfter) >= 0) {
            hop(order, CHANGE_TYPE_CJ_STALL, MSG_WARNING_PREFIX + failingSince + " — ops notified", "system", now);
            opsNotifier.notify("CJ placement failing — order " + order.getOrderSn(),
                    "Placement of PAID order " + orderId.getId() + " (sn " + order.getOrderSn()
                    + ") has been failing with retryable errors since " + failingSince
                    + " (" + failing.toMinutes() + " min).\nLast error: " + lastError
                    + "\n\nIt keeps retrying every sweep and will be parked after " + parkAfter.toHours()
                    + " h. Nothing to do if CJ is merely down; check CJ credentials/account otherwise.");
            log.warn("CJ placement for order {} failing since {} — ops warned; last error: {}",
                    orderId.getId(), failingSince, lastError);
            return StallVerdict.WARNED;
        }
        return StallVerdict.RETRYING;
    }

    // ------------------------------------------------------------------
    // Placed, but CJ will not move it (confirm / pay-from-balance keep failing)
    // ------------------------------------------------------------------

    /**
     * A CJ lifecycle mutation (confirm, pay from balance) failed. The sync keeps retrying —
     * that is correct — but it must be VISIBLE: one timeline hop + ops mail, repeated at
     * most every {@code lifecycleAlertEvery}. Insufficient CJ balance is the expected cause.
     */
    public void onLifecycleMutationFailure(LitemallOrderAggregate order, String operation, String reason) {
        LitemallOrderId orderId = order.getOrderId();
        String prefix = "CJ " + operation + " failed";
        LocalDateTime now = LocalDateTime.now();
        for (LitemallOrderStatusChange hop : statusHistoryRepository.findByOrderId(orderId)) {
            String msg = hop.getChangeMessage() == null ? "" : hop.getChangeMessage();
            if (CHANGE_TYPE_CJ_STALL.equals(hop.getChangeType()) && msg.startsWith(prefix)
                    && hop.getChangeTime() != null
                    && Duration.between(hop.getChangeTime(), now).compareTo(lifecycleAlertEvery) < 0) {
                return; // already told, recently
            }
        }
        hop(order, CHANGE_TYPE_CJ_STALL, prefix + ": " + reason + " — retrying automatically", "system", now);
        opsNotifier.notify("CJ " + operation + " failing — order " + order.getOrderSn(),
                "CJ order " + order.getCjOrderId() + " (local " + orderId.getId() + ", sn " + order.getOrderSn()
                + ") is placed but CJ refuses '" + operation + "': " + reason
                + "\n\nThe sync retries every sweep. If this is the account balance, top it up in the CJ "
                + "dashboard; the order proceeds on its own once CJ accepts. The customer's payment is NOT touched.");
        log.warn("CJ {} failing for order {} (cj {}): {}", operation, orderId.getId(), order.getCjOrderId(), reason);
    }

    // ------------------------------------------------------------------
    // CJ cancelled a paid order on its side
    // ------------------------------------------------------------------

    /**
     * Exactly once, on the transition (the caller has already written the {@code cj_sync}
     * hop and the projection): tell ops, and — decision D2 — tell the customer. No money
     * moves; the refund is settled by a human through the normal refund path.
     */
    public void onCjCancelledAfterPayment(LitemallOrderAggregate order) {
        LitemallOrderId orderId = order.getOrderId();
        opsNotifier.notify("CJ cancelled order " + order.getOrderSn(),
                "CJ reports order " + orderId.getId() + " (sn " + order.getOrderSn()
                + ", CJ id " + order.getCjOrderId() + ") as CANCELLED on the CJ side."
                + "\nLocal order status: " + order.getOrderStatus()
                + " — the customer's payment is NOT touched automatically."
                + "\nThe customer has been emailed that the order could not be fulfilled and that support will "
                + "contact them. Decide and settle via the normal refund/aftersale path.");
        domainEventPublisher.publish(new LitemallCjFulfilmentCancelledEvent(orderId, order.getCjOrderId()));
        log.warn("CJ order {} (local {}) CANCELLED at CJ — customer + ops notified; refund is a human decision",
                order.getCjOrderId(), orderId.getId());
    }

    private void hop(LitemallOrderAggregate order, String changeType, String message, String operator,
                     LocalDateTime at) {
        LitemallOrderStatus local = order.getOrderStatus();
        statusHistoryRepository.record(new LitemallOrderStatusChange(
                order.getOrderId(), local, local, changeType, message, operator, at));
    }
}
