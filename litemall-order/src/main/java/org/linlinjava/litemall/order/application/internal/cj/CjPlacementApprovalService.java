package org.linlinjava.litemall.order.application.internal.cj;

import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Wave 23 (V59): the admin CJ-placement approval surface. Backs the
 * {@code /srv/private/admin/order/cj-placement/*} endpoints:
 *
 * <ul>
 *   <li>{@link #pending}: paid CJ orders not yet placed and not yet approved, oldest
 *       payment first, with an honest per-order readiness verdict ({@code cjReady} =
 *       every line resolves to a CJ variant — LOCAL reads only, never a CJ call on a
 *       request path) and a {@code holdReason} for anything an admin should look at
 *       before approving (earlier CJ rejection, unresolvable variants, open aftersale,
 *       EU/IOSS configuration).</li>
 *   <li>{@link #approve}: CAS-stamps {@code cj_placement_approved_time/_by} exactly once.
 *       Idempotent — re-approving returns the existing stamp; refusals are TYPED (not a
 *       CJ order / not paid / already placed) so the admin UI can show them verbatim.
 *       The stamp alone changes nothing at CJ: the {@link CjPlacementSweepScheduler}
 *       places the order on its next tick (or parks it in the usual typed retryable
 *       state while CJ is disabled/down — never a fake success).</li>
 * </ul>
 */
@Service
public class CjPlacementApprovalService {

    private static final Logger log = LoggerFactory.getLogger(CjPlacementApprovalService.class);

    /** EU members (ISO-3166 alpha-2) — CJ createOrderV2 wants an IOSS declaration for these. */
    private static final Set<String> EU_COUNTRIES = Set.of(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE",
            "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE");

    private final LitemallOrderRepository orderRepository;
    private final LitemallOrderGoodsRepository orderGoodsRepository;
    private final LitemallOrderStatusHistoryRepository statusHistoryRepository;
    private final CjOrderLineResolver lineResolver;

    /** Mirror of the facade's IOSS config: 0 = nothing sent, CJ dashboard default applies. */
    private final int iossType;

    public CjPlacementApprovalService(LitemallOrderRepository orderRepository,
                                      LitemallOrderGoodsRepository orderGoodsRepository,
                                      LitemallOrderStatusHistoryRepository statusHistoryRepository,
                                      CjOrderLineResolver lineResolver,
                                      @Value("${spring.cjdropship.api.ioss-type:0}") int iossType) {
        this.orderRepository = orderRepository;
        this.orderGoodsRepository = orderGoodsRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.lineResolver = lineResolver;
        this.iossType = iossType;
    }

    // ------------------------------------------------------------------
    // Pending list
    // ------------------------------------------------------------------

    /** One pending order with its lines and readiness verdict. */
    public record PendingOrder(LitemallOrderAggregate order,
                               List<LitemallOrderGoodsAggregate> items,
                               boolean cjReady,
                               String holdReason,
                               boolean parked,
                               String parkReason) {
    }

    public record PendingPage(List<PendingOrder> list, long total) {
    }

    public PendingPage pending(int page, int limit) {
        List<LitemallOrderAggregate> orders = orderRepository.queryCjPlacementPending(page, limit);
        long total = orderRepository.countCjPlacementPending();
        List<PendingOrder> rows = new ArrayList<>(orders.size());
        for (LitemallOrderAggregate order : orders) {
            rows.add(assess(order));
        }
        return new PendingPage(rows, total);
    }

    private PendingOrder assess(LitemallOrderAggregate order) {
        List<LitemallOrderGoodsAggregate> items = orderGoodsRepository.findByOId(order.getOrderId());
        boolean cjReady = true;
        List<String> holds = new ArrayList<>();

        // Parked (terminal CJ rejection, or a day of failed retries): approving does
        // nothing — the admin action is REQUEUE (F7). Say so, with CJ's own words.
        boolean parked = CjPlacementService.isParked(order.getCjOrderStatus());
        String parkReason = null;
        if (parked) {
            cjReady = false;
            parkReason = lastParkMessage(order.getOrderId());
            holds.add((CjPlacementService.STATUS_PLACEMENT_REJECTED.equals(order.getCjOrderStatus())
                    ? "CJ rejected the placement"
                    : "placement kept failing and was parked")
                    + (parkReason == null ? "" : ": " + parkReason)
                    + " — fix the cause and use Requeue (approval alone does nothing)");
        }

        // Variant resolution — the same LOCAL check the fulfilment path performs at
        // placement (cj_vid on every line). No CJ HTTP here.
        List<String> unresolvable = new ArrayList<>();
        for (LitemallOrderGoodsAggregate item : items) {
            Integer productId = item.getProductId() == null ? null : item.getProductId().getId();
            try {
                lineResolver.resolveVid(productId);
            } catch (LitemallCjOrderException e) {
                unresolvable.add(StringUtils.hasText(item.getGoodsName())
                        ? item.getGoodsName() : "product " + productId);
            }
        }
        if (!unresolvable.isEmpty()) {
            cjReady = false;
            holds.add("variant not resolvable at CJ (missing cj_vid — not yet enriched): "
                    + String.join(", ", unresolvable));
        }

        // Open aftersale: the customer asked for a refund on this paid order —
        // an admin should settle that before shipping it into fulfilment.
        LitemallAfterSaleStatus aftersale = order.getAfterSaleStatus();
        if (aftersale == LitemallAfterSaleStatus.STATUS_REQUEST
                || aftersale == LitemallAfterSaleStatus.STATUS_RECEPT) {
            holds.add("aftersale/refund request in progress — resolve it before approving fulfilment");
        }

        // EU destination without an explicit IOSS declaration configured: CJ falls
        // back to its dashboard IOSS Option. Informational, not blocking.
        String country = order.getCountryCode();
        if (country != null && EU_COUNTRIES.contains(country.trim().toUpperCase()) && iossType == 0) {
            holds.add("EU destination with no explicit IOSS configured — CJ's dashboard IOSS option applies");
        }

        return new PendingOrder(order, items, cjReady,
                holds.isEmpty() ? null : String.join("; ", holds), parked, parkReason);
    }

    /** The message of the latest {@code cj_placement_failed} hop — CJ's reason, verbatim. */
    private String lastParkMessage(LitemallOrderId orderId) {
        try {
            String last = null;
            for (LitemallOrderStatusChange hop : statusHistoryRepository.findByOrderId(orderId)) {
                if (CjPlacementService.CHANGE_TYPE_CJ_PLACEMENT_FAILED.equals(hop.getChangeType())) {
                    last = hop.getChangeMessage();
                }
            }
            return last;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Approve
    // ------------------------------------------------------------------

    public enum ApproveStatus {
        /** Stamp written now. */
        APPROVED,
        /** Stamp already present — idempotent success, existing stamp returned. */
        ALREADY_APPROVED,
        NOT_FOUND,
        NOT_CJ,
        NOT_PAID,
        ALREADY_PLACED,
        /** A refund/aftersale request is open — settle it first (F9). */
        AFTERSALE_OPEN,
        /** Parked under a placement sentinel — approval does nothing; use requeue (F7). */
        PARKED
    }

    public record ApproveResult(ApproveStatus status, String message,
                                LocalDateTime approvedTime, String approvedBy) {
        static ApproveResult refuse(ApproveStatus status, String message) {
            return new ApproveResult(status, message, null, null);
        }
    }

    /**
     * Stamp admin approval on one order. Never places anything itself — the sweep does
     * that on its next tick, keeping all CJ traffic on the paced background path.
     */
    public ApproveResult approve(Integer orderId, String adminUserId) {
        LitemallOrderAggregate order = orderRepository.findById(new LitemallOrderId(orderId)).orElse(null);
        if (order == null) {
            return ApproveResult.refuse(ApproveStatus.NOT_FOUND, "order " + orderId + " not found");
        }
        ApproveResult refusal = eligibilityRefusal(order);
        if (refusal != null) {
            return refusal;
        }
        if (order.getCjPlacementApprovedTime() != null) {
            return alreadyApproved(order);
        }
        String approvedBy = StringUtils.hasText(adminUserId) ? adminUserId.trim() : "admin";
        int stamped = orderRepository.stampCjPlacementApproval(order.getOrderId(), approvedBy);
        // CAS lost or state moved between read and write: re-read and answer from the row.
        LitemallOrderAggregate fresh = orderRepository.findById(order.getOrderId()).orElse(null);
        if (stamped == 0) {
            if (fresh != null && fresh.getCjPlacementApprovedTime() != null) {
                return alreadyApproved(fresh); // concurrent approval won — same outcome
            }
            refusal = fresh == null
                    ? ApproveResult.refuse(ApproveStatus.NOT_FOUND, "order " + orderId + " not found")
                    : eligibilityRefusal(fresh);
            return refusal != null ? refusal
                    : ApproveResult.refuse(ApproveStatus.NOT_PAID,
                            "order " + orderId + " is no longer approvable");
        }
        LocalDateTime approvedTime = fresh != null && fresh.getCjPlacementApprovedTime() != null
                ? fresh.getCjPlacementApprovedTime() : LocalDateTime.now();
        recordApprovalHop(order, approvedBy);
        log.info("CJ placement APPROVED for order {} (sn {}) by admin {}",
                orderId, order.getOrderSn(), approvedBy);
        return new ApproveResult(ApproveStatus.APPROVED,
                "approved — the placement sweep will send it to CJ on its next run",
                approvedTime, approvedBy);
    }

    /** The typed not-eligible refusals, shared between first check and post-CAS re-check. */
    private ApproveResult eligibilityRefusal(LitemallOrderAggregate order) {
        if (!order.isCjFulfilled()) {
            return ApproveResult.refuse(ApproveStatus.NOT_CJ,
                    "order " + order.getOrderId().getId() + " is not a CJ-fulfilled order");
        }
        if (StringUtils.hasText(order.getCjOrderId())) {
            return ApproveResult.refuse(ApproveStatus.ALREADY_PLACED,
                    "order " + order.getOrderId().getId() + " is already placed at CJ ("
                            + order.getCjOrderId() + ")");
        }
        if (order.getOrderStatus() != LitemallOrderStatus.PAID) {
            return ApproveResult.refuse(ApproveStatus.NOT_PAID,
                    "order " + order.getOrderId().getId() + " is not in the paid state (current: "
                            + order.getOrderStatus() + ")");
        }
        LitemallAfterSaleStatus aftersale = order.getAfterSaleStatus();
        if (aftersale == LitemallAfterSaleStatus.STATUS_REQUEST || aftersale == LitemallAfterSaleStatus.STATUS_RECEPT) {
            return ApproveResult.refuse(ApproveStatus.AFTERSALE_OPEN,
                    "order " + order.getOrderId().getId() + " has an open refund/aftersale request — "
                            + "settle it before sending the order to fulfilment");
        }
        if (CjPlacementService.isParked(order.getCjOrderStatus())) {
            return ApproveResult.refuse(ApproveStatus.PARKED,
                    "order " + order.getOrderId().getId() + " is parked (" + order.getCjOrderStatus()
                            + ") — approval does nothing; fix the cause and requeue it");
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Requeue (F7)
    // ------------------------------------------------------------------

    public enum RequeueStatus { REQUEUED, NOT_FOUND, NOT_PARKED }

    public record RequeueResult(RequeueStatus status, String message) {
    }

    /**
     * Clear a placement park sentinel so the sweep picks the order up again. CAS on the
     * sentinel, so a concurrent requeue or a placement that already happened answers
     * NOT_PARKED instead of clobbering. Does NOT reset the approval stamp: an order that was
     * approved stays approved. Replaces the hand-written {@code UPDATE ... SET cj_order_status
     * = NULL} that used to travel by ops mail.
     */
    public RequeueResult requeue(Integer orderId, String adminUserId) {
        LitemallOrderId id = new LitemallOrderId(orderId);
        LitemallOrderAggregate order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return new RequeueResult(RequeueStatus.NOT_FOUND, "order " + orderId + " not found");
        }
        if (!CjPlacementService.isParked(order.getCjOrderStatus()) || StringUtils.hasText(order.getCjOrderId())
                || order.getOrderStatus() != LitemallOrderStatus.PAID) {
            return new RequeueResult(RequeueStatus.NOT_PARKED,
                    "order " + orderId + " is not parked (cj status " + order.getCjOrderStatus()
                            + ", order status " + order.getOrderStatus() + ")");
        }
        int cleared = orderRepository.clearCjPlacementSentinel(id);
        if (cleared == 0) {
            return new RequeueResult(RequeueStatus.NOT_PARKED, "order " + orderId + " is no longer parked");
        }
        String by = StringUtils.hasText(adminUserId) ? adminUserId.trim() : "admin";
        try {
            LitemallOrderStatus local = order.getOrderStatus();
            statusHistoryRepository.record(new LitemallOrderStatusChange(id, local, local,
                    CjPlacementService.CHANGE_TYPE_CJ_PLACEMENT,
                    CjFulfilmentIncidentService.MSG_REQUEUED_PREFIX + " by admin " + by,
                    "admin:" + by, LocalDateTime.now()));
        } catch (RuntimeException e) {
            log.warn("requeue timeline hop failed for order {} (sentinel is cleared): {}", orderId, e.getMessage());
        }
        log.info("CJ placement REQUEUED for order {} (sn {}) by admin {}", orderId, order.getOrderSn(), by);
        return new RequeueResult(RequeueStatus.REQUEUED,
                "requeued — the placement sweep will retry it on its next run");
    }

    private ApproveResult alreadyApproved(LitemallOrderAggregate order) {
        return new ApproveResult(ApproveStatus.ALREADY_APPROVED,
                "already approved", order.getCjPlacementApprovedTime(), order.getCjPlacementApprovedBy());
    }

    /** Timeline hop so the approval shows up in the order's customer/admin history. */
    private void recordApprovalHop(LitemallOrderAggregate order, String approvedBy) {
        try {
            LitemallOrderStatus local = order.getOrderStatus();
            statusHistoryRepository.record(new LitemallOrderStatusChange(
                    order.getOrderId(), local, local, CjPlacementService.CHANGE_TYPE_CJ_PLACEMENT,
                    "Approved for CJ fulfilment by admin " + approvedBy,
                    "admin:" + approvedBy, LocalDateTime.now()));
        } catch (RuntimeException e) {
            log.warn("approval timeline hop failed for order {} (approval itself is stamped): {}",
                    order.getOrderId().getId(), e.getMessage());
        }
    }
}
