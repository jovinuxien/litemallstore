package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.db.dao.LitemallUserBrokerageRecordMapper;
import org.linlinjava.litemall.db.domain.LitemallUserBrokerageRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Matures frozen commissions whose freeze window has elapsed (the CJ 5-minute
 * sweep pattern, {@link org.linlinjava.litemall.order.application.internal.cj.CjOrderStatusSyncScheduler}):
 * the due set is read straight off the ledger, each row is settled in ITS OWN
 * transaction ({@link BrokerageService#unfreeze} — guarded 0→1 flip + balance
 * credit), and one row's failure never poisons the sweep. A raced row (concurrent
 * sweep, aftersale clawback) is a 0-row flip → skipped, never retry-credited; a
 * failed credit rolls the flip back and the row is simply due again next sweep.
 */
@Component
public class BrokerageUnfreezeScheduler {

    private static final Logger log = LoggerFactory.getLogger(BrokerageUnfreezeScheduler.class);

    private final LitemallUserBrokerageRecordMapper recordMapper;
    private final BrokerageService brokerageService;

    /** Max due rows visited per sweep (oldest unfreeze_time first — nobody starves). */
    @Value("${litemall.order.brokerage-unfreeze-batch:200}")
    private int batchSize;

    public BrokerageUnfreezeScheduler(LitemallUserBrokerageRecordMapper recordMapper,
                                      BrokerageService brokerageService) {
        this.recordMapper = recordMapper;
        this.brokerageService = brokerageService;
    }

    @Scheduled(fixedDelayString = "${litemall.order.brokerage-unfreeze-sweep-ms:300000}")
    public void sweep() {
        List<LitemallUserBrokerageRecord> due = recordMapper.selectUnfreezable(LocalDateTime.now(), batchSize);
        if (due == null || due.isEmpty()) {
            return;
        }
        int credited = 0;
        for (LitemallUserBrokerageRecord record : due) {
            try {
                if (brokerageService.unfreeze(record)) {
                    credited++;
                }
            } catch (RuntimeException e) {
                log.warn("Brokerage unfreeze failed for record {} (user {}); will retry next sweep: {}",
                        record.getId(), record.getUserId(), e.getMessage());
            }
        }
        log.info("Brokerage unfreeze sweep: {} due, {} credited", due.size(), credited);
    }
}
