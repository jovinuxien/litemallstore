package org.linlinjava.litemall.goods.application.inventoryflow;

import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Nightly targeted availability recheck (Fisher et al. ch15, polling consumer): enqueues the
 * CJ variant ids of DEAL-RELEVANT goods only (live price-swapped deals + 7-day proposed
 * candidates) into the bounded recheck queue; a 1 msg/s poller drains it through
 * {@code getInventory(vid)} — respecting the global CJ 1 req/s budget. Cron 04:15 by default,
 * clear of the 02:45/03:00/03:30 slots. Never touches the request path.
 */
@Component
public class DealInventoryRecheckTask {

    private static final Logger log = LoggerFactory.getLogger(DealInventoryRecheckTask.class);

    private final InsightMapper insightMapper;
    private final CJDropshippingConfig cjConfig;
    private final MessageChannel recheckChannel;

    public DealInventoryRecheckTask(InsightMapper insightMapper,
                                    CJDropshippingConfig cjConfig,
                                    @Qualifier("invflow.recheck") MessageChannel recheckChannel) {
        this.insightMapper = insightMapper;
        this.cjConfig = cjConfig;
        this.recheckChannel = recheckChannel;
    }

    @Scheduled(cron = "${litemall.inventoryflow.recheck-cron:0 15 4 * * *}")
    public void enqueueRechecks() {
        if (!cjConfig.isEnabled()) {
            return;
        }
        List<String> vids;
        try {
            vids = insightMapper.selectTrackedDealVids();
        } catch (RuntimeException ex) {
            log.warn("deal recheck: tracked-vid query failed: {}", ex.getMessage());
            return;
        }
        int queued = 0;
        int dropped = 0;
        for (String vid : vids) {
            // Non-blocking offer: a full queue drops the tail (logged) rather than stalling the
            // scheduler thread; the next night converges.
            if (recheckChannel.send(MessageBuilder.withPayload(vid).build(), 0)) {
                queued++;
            } else {
                dropped++;
            }
        }
        if (dropped > 0) {
            log.warn("deal recheck: queue full — {} of {} vids dropped this night", dropped, vids.size());
        }
        log.info("deal recheck: {} variant ids queued for paced inventory refresh", queued);
    }
}
