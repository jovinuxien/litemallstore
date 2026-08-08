package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.application.util.exception.payment.LitemallRefundFailedException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * Wave 21: applies promotion's {@code GROUP_EXPIRED} verdict to the local orders
 * that rode the failed group. Per member slot ({@code memberPinkIds[]} in the
 * event; the leader's own {@code groupPinkId} rides the same list):
 *
 * <ul>
 *   <li><b>PAID</b> order → auto-cancel + refund to the ORIGINAL tender through the
 *       existing tender-parity refund path
 *       ({@link LitemallOrderOrchestratorService#autoRefundForExpiredGroup}) — the
 *       REFUNDED flip's domain event drives the existing customer-mail trigger;</li>
 *   <li><b>CREATED</b> (unpaid) order → plain system cancel
 *       ({@link LitemallOrderServiceImpl#autoCancelOrder}, its own transaction);</li>
 *   <li>already REFUNDED / cancelled → idempotent skip (a replayed event is a
 *       no-op); anything else (e.g. SHIPPED) is logged and left alone — money that
 *       moved for a shipped parcel is an aftersale decision, not an auto-refund.</li>
 * </ul>
 *
 * <p>Failures are per-order: a PSP refusal leaves THAT order in REFUND_REQUEST
 * (visible, admin-retryable) and processing continues. Honest counts are logged at
 * the end of every event.
 */
@Service
public class GroupExpiredOrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(GroupExpiredOrderProcessor.class);

    static final String REASON = "Group-buy did not fill before it expired — automatic cancellation";

    private final LitemallOrderRepository orderRepository;
    private final LitemallOrderServiceImpl orderService;
    private final LitemallOrderOrchestratorService orchestratorService;

    public GroupExpiredOrderProcessor(LitemallOrderRepository orderRepository,
                                      LitemallOrderServiceImpl orderService,
                                      LitemallOrderOrchestratorService orchestratorService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.orchestratorService = orchestratorService;
    }

    /** Process every member slot of one expired group; never throws. */
    public void processExpiredGroup(Collection<Integer> pinkIds) {
        int refunded = 0;
        int cancelled = 0;
        int alreadyDone = 0;
        int noOrder = 0;
        int skipped = 0;
        int failed = 0;
        for (Integer pinkId : pinkIds) {
            if (pinkId == null || pinkId <= 0) {
                continue;
            }
            List<LitemallOrderAggregate> orders = orderRepository.findByPinkId(pinkId);
            if (orders == null || orders.isEmpty()) {
                noOrder++;
                continue;
            }
            for (LitemallOrderAggregate order : orders) {
                LitemallOrderId orderId = order.getOrderId();
                LitemallOrderStatus status = order.getOrderStatus();
                try {
                    switch (status) {
                        case PAID:
                        case REFUND_REQUEST:
                            LitemallOrderOperationResult result =
                                    orchestratorService.autoRefundForExpiredGroup(orderId, REASON);
                            if (result.isSuccess()) {
                                refunded++;
                            } else {
                                failed++;
                                log.warn("GROUP_EXPIRED: refund of order {} (pink {}) refused: {}",
                                        orderId.getId(), pinkId, result.getMessage());
                            }
                            break;
                        case CREATED:
                            // Unpaid: just cancel (own REQUIRES_NEW transaction; skips
                            // gracefully if the customer pays in the race window — the
                            // paid order is then caught by a replay/manual pass).
                            orderService.autoCancelOrder(orderId, REASON);
                            cancelled++;
                            break;
                        case REFUNDED:
                        case CANCELED:
                        case SYSTEM_CANCELED:
                            alreadyDone++; // replayed event — idempotent no-op
                            break;
                        default:
                            skipped++;
                            log.warn("GROUP_EXPIRED: order {} (pink {}) in state {} left untouched — "
                                            + "resolve via the aftersale/refund flow if money must move",
                                    orderId.getId(), pinkId, status);
                    }
                } catch (LitemallRefundFailedException e) {
                    failed++;
                    log.error("GROUP_EXPIRED: refund of order {} (pink {}) FAILED at the payment "
                                    + "provider — left in REFUND_REQUEST for admin retry: {}",
                            orderId.getId(), pinkId, e.getMessage());
                } catch (RuntimeException e) {
                    failed++;
                    log.error("GROUP_EXPIRED: processing order {} (pink {}) failed — event replay "
                            + "or manual handling needed", orderId.getId(), pinkId, e);
                }
            }
        }
        log.info("GROUP_EXPIRED processed {} member slot(s): {} refunded, {} cancelled (unpaid), "
                        + "{} already settled, {} without a local order, {} left untouched, {} failed",
                pinkIds.size(), refunded, cancelled, alreadyDone, noOrder, skipped, failed);
    }
}
