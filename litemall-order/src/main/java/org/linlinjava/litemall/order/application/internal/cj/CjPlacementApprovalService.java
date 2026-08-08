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
                               String holdReason) {
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

        // Earlier terminal CJ rejection: approving alone will NOT requeue it — the
        // PLACEMENT_REJECTED sentinel must be cleared through ops (documented in the
        // ops mail the rejection sent). Say so instead of letting an approval no-op.
        if (CjPlacementService.STATUS_PLACEMENT_REJECTED.equals(order.getCjOrderStatus())) {
            cjReady = false;
            holds.add("CJ terminally rejected an earlier placement attempt (see order timeline); "
                    + "clear the PLACEMENT_REJECTED sentinel to requeue");
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
                holds.isEmpty() ? null : String.join("; ", holds));
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
        ALREADY_PLACED
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
        return null;
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
