package org.linlinjava.litemall.goods.application.inventoryflow;

import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.db.dao.LitemallProductMetricDailyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Service activator (Fisher et al. ch5) for VANISHED pids: today's metric row drops to
 * {@code available = 0}. The goods row itself is already handled by the existing reconcile
 * (soft-delete) + on-sale enforcement — vanished goods stay viewable but unbuyable; this
 * records the availability history the insight series reads. Lookup includes soft-deleted
 * goods (the vanished product's row usually IS soft-deleted by the time this runs).
 */
@Component
public class AvailabilityRecorder {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityRecorder.class);

    private final LitemallCjLinkageMapper linkageMapper;
    private final LitemallProductMetricDailyMapper metricMapper;

    public AvailabilityRecorder(LitemallCjLinkageMapper linkageMapper,
                                LitemallProductMetricDailyMapper metricMapper) {
        this.linkageMapper = linkageMapper;
        this.metricMapper = metricMapper;
    }

    public ProductFlowEvent markVanished(ProductFlowEvent event) {
        try {
            Integer goodsId = linkageMapper.findAnyGoodsIdByCjPid(event.pid());
            if (goodsId != null) {
                metricMapper.upsertAvailability(goodsId, LocalDate.now(), false);
            }
        } catch (RuntimeException ex) {
            log.warn("inventory flow: vanished-mark failed for pid {}: {}", event.pid(), ex.getMessage());
        }
        return event;
    }
}
