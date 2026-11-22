package org.linlinjava.litemall.order.application.internal.cj;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjTrackingFacade;
import org.linlinjava.litemall.order.infrastructure.services.cj.CjTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hourly look at shipped CJ orders that have a tracking number and have not yet been told
 * their local carrier: asks CJ tracking once per such order and lets
 * {@link LocalCarrierNoticeService} raise the one-time notice when CJ names the carrier.
 *
 * <p>CJ budget: one {@code trackInfo} call per untold order per sweep, spaced ~1.1 s apart
 * (CJ's ~1 QPS account-wide limit) and capped per sweep; an order that has been told costs
 * no CJ call at all. The facade never throws and caches for an hour, so the order page and
 * this sweep share answers. One order's failure never stops the sweep.
 */
@Component
public class LocalCarrierNoticeScheduler {

    private static final Logger log = LoggerFactory.getLogger(LocalCarrierNoticeScheduler.class);

    private final LitemallOrderRepository orderRepository;
    private final CjTrackingFacade trackingFacade;
    private final LocalCarrierNoticeService noticeService;
    private final CjTokenService cjTokenService;

    /** Kill switch (container recreate only). */
    @Value("${litemall.order.cj.local-carrier-notice-enabled:true}")
    private boolean enabled = true;

    /** Max CJ tracking calls per sweep. */
    @Value("${litemall.order.cj.local-carrier-notice-batch:50}")
    private int batchSize = 50;

    /** Pause between CJ calls; tests set 0. */
    long paceMs = 1100;

    public LocalCarrierNoticeScheduler(LitemallOrderRepository orderRepository,
                                       CjTrackingFacade trackingFacade,
                                       LocalCarrierNoticeService noticeService,
                                       CjTokenService cjTokenService) {
        this.orderRepository = orderRepository;
        this.trackingFacade = trackingFacade;
        this.noticeService = noticeService;
        this.cjTokenService = cjTokenService;
    }

    @Scheduled(initialDelayString = "${litemall.order.cj.local-carrier-notice-initial-delay-ms:120000}",
            fixedDelayString = "${litemall.order.cj.local-carrier-notice-sweep-ms:3600000}")
    public void sweep() {
        if (!enabled || !cjTokenService.isEnabled()) {
            return;
        }
        List<LitemallOrderAggregate> shipped = orderRepository.queryShippedWithTracking();
        if (shipped == null || shipped.isEmpty()) {
            return;
        }
        int asked = 0;
        int told = 0;
        for (LitemallOrderAggregate order : shipped) {
            if (!order.isCjFulfilled()) {
                continue;
            }
            try {
                if (noticeService.alreadyTold(order)) {
                    continue;
                }
                if (asked >= batchSize) {
                    log.info("Local-carrier sweep: batch cap {} reached; the rest wait for the next sweep", batchSize);
                    break;
                }
                if (asked > 0) {
                    pace();
                }
                asked++;
                if (noticeService.tellIfAssigned(order, trackingFacade.trackInfo(order.getShipSn()).orElse(null))) {
                    told++;
                }
            } catch (RuntimeException e) {
                log.warn("Local-carrier check failed for order {}; will retry next sweep",
                        order.getOrderId().getId(), e);
            }
        }
        if (asked > 0) {
            log.info("Local-carrier sweep: asked CJ about {} shipped orders, {} newly told", asked, told);
        }
    }

    private void pace() {
        if (paceMs <= 0) {
            return;
        }
        try {
            Thread.sleep(paceMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
