package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.db.dao.LitemallUserBrokerageRecordMapper;
import org.linlinjava.litemall.db.dao.LitemallUserMapper;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.domain.LitemallUserBrokerageRecord;
import org.linlinjava.litemall.db.service.LitemallSystemConfigService;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderPaidEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wave-5 affiliate brokerage engine: awards a FROZEN commission to the buyer's
 * referrer when an order's payment commits, and owns the ledger's guarded
 * lifecycle transitions (unfreeze credit, aftersale clawback).
 *
 * <p>Wiring mirrors {@link OrderPaidReceiptPrintListener} (the Wave-4 auto-print
 * pattern): {@code @TransactionalEventListener(AFTER_COMMIT)} on
 * {@link LitemallOrderPaidEvent} — which fires at most once per order thanks to the
 * pay path's guarded 0-row UPDATE — hands off to a small dedicated executor and the
 * worker catch-alls every throwable into a WARN. PAYMENT MUST NEVER BLOCK OR FAIL
 * BECAUSE OF COMMISSION BOOKKEEPING.
 *
 * <p>Config ({@code litemall_brokerage_*} rows seeded by V39) is read through
 * {@link LitemallSystemConfigService} PER EVENT — the litemall-core
 * {@code SystemConfig} static cache is per-JVM and goes stale the moment the admin
 * console (a different service) updates a value.
 */
@Service
public class BrokerageService {

    private static final Logger log = LoggerFactory.getLogger(BrokerageService.class);

    public static final String KEY_ENABLED = "litemall_brokerage_enabled";
    public static final String KEY_RATE = "litemall_brokerage_rate";
    public static final String KEY_FREEZE_DAYS = "litemall_brokerage_freeze_days";
    public static final String KEY_MIN_EXTRACT = "litemall_brokerage_min_extract";

    private final LitemallOrderRepository orderRepository;
    private final LitemallUserMapper userMapper;
    private final LitemallUserBrokerageRecordMapper recordMapper;
    private final LitemallSystemConfigService systemConfigService;

    /** 1 daemon worker, bounded queue: commission bookkeeping never backs up payments. */
    private final ThreadPoolExecutor executor;

    public BrokerageService(LitemallOrderRepository orderRepository,
                            LitemallUserMapper userMapper,
                            LitemallUserBrokerageRecordMapper recordMapper,
                            LitemallSystemConfigService systemConfigService) {
        this.orderRepository = orderRepository;
        this.userMapper = userMapper;
        this.recordMapper = recordMapper;
        this.systemConfigService = systemConfigService;
        AtomicInteger counter = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                runnable -> {
                    Thread thread = new Thread(runnable, "brokerage-award-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                // Unlike a lost receipt, a dropped commission is not user-recoverable, so
                // never discard silently: WARN with enough context for a manual repair.
                (runnable, pool) -> log.warn("Brokerage award queue full (size {}); dropping one award task — "
                        + "reconcile litemall_user_brokerage_record against recent paid orders", pool.getQueue().size()));
    }

    // ------------------------------------------------------------------
    // Commission award (frozen at pay)
    // ------------------------------------------------------------------

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onOrderPaid(LitemallOrderPaidEvent event) {
        try {
            executor.execute(() -> award(event));
        } catch (RuntimeException e) {
            // Even a rejected submit must never surface to the (already committed) payment.
            log.warn("Brokerage award enqueue failed for order {}: {}",
                    event.getOrderId().getId(), e.getMessage());
        }
    }

    private void award(LitemallOrderPaidEvent event) {
        try {
            BrokerageConfig config = loadConfig();
            if (!config.enabled()) {
                return; // kill switch: pay writes no row
            }
            LitemallOrderAggregate order = orderRepository.findById(event.getOrderId()).orElse(null);
            if (order == null) {
                log.warn("Brokerage award skipped: order {} not found after commit", event.getOrderId().getId());
                return;
            }
            Integer buyerId = order.getUserId() == null ? null : order.getUserId().getId();
            if (buyerId == null) {
                return;
            }
            Integer spreadUid = userMapper.selectSpreadUidByUserId(buyerId);
            if (spreadUid == null || spreadUid <= 0) {
                return; // buyer has no referrer
            }
            LitemallUser promoter = userMapper.selectLivePromoter(spreadUid);
            if (promoter == null) {
                // Referrer bound at registration but since deleted or demoted — no commission.
                log.info("Brokerage award skipped for order {}: referrer {} is not a live promoter",
                        order.getOrderSn(), spreadUid);
                return;
            }
            BigDecimal goodsPrice = order.getGoodsPrice() == null ? null : order.getGoodsPrice().getAmount();
            if (goodsPrice == null || goodsPrice.signum() <= 0) {
                return;
            }
            BigDecimal commission = goodsPrice.multiply(config.rate())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (commission.signum() <= 0) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            LitemallUserBrokerageRecord record = new LitemallUserBrokerageRecord();
            record.setUserId(spreadUid);
            record.setLinkId(order.getOrderSn());
            record.setLinkType(LitemallUserBrokerageRecord.LINK_TYPE_ORDER);
            record.setPm(LitemallUserBrokerageRecord.PM_INCOME);
            record.setTitle("Order commission");
            record.setPrice(commission);
            // Snapshot of the promoter's available balance at write time; the money
            // itself only lands in brokerage_price when the unfreeze sweep matures it.
            record.setBalance(promoter.getBrokeragePrice() == null ? BigDecimal.ZERO : promoter.getBrokeragePrice());
            record.setMark(config.rate().stripTrailingZeros().toPlainString() + "% of goods total "
                    + goodsPrice + " for order " + order.getOrderSn() + " (buyer #" + buyerId + ")");
            record.setStatus(LitemallUserBrokerageRecord.STATUS_FROZEN);
            record.setFreezeTime(now);
            record.setUnfreezeTime(now.plusDays(config.freezeDays()));
            record.setAddTime(now);
            record.setUpdateTime(now);
            record.setDeleted(false);
            recordMapper.insert(record);
            log.info("Brokerage: froze {} for promoter {} on order {} (unfreezes {})",
                    commission, spreadUid, order.getOrderSn(), record.getUnfreezeTime());
        } catch (Throwable t) {
            // Catch-ALL: a bookkeeping bug must never kill the single worker thread.
            log.warn("Brokerage award crashed for order {}: {}", event.getOrderId().getId(), t.toString());
        }
    }

    // ------------------------------------------------------------------
    // Aftersale clawback (called inside the approveAftersale transaction)
    // ------------------------------------------------------------------

    /**
     * Invalidate the still-frozen commission of one order (guarded {@code status=0}
     * → −1; joins the caller's transaction). An already-unfrozen row stays VALID by
     * design — the promoter may have withdrawn the money; that is exactly why the
     * freeze window exists (see the brokerage lifecycle ADR).
     *
     * @return number of rows invalidated (0 = nothing frozen to claw back)
     */
    public int invalidateFrozenForOrder(String orderSn) {
        int rows = recordMapper.invalidateFrozenByOrder(orderSn);
        if (rows > 0) {
            log.info("Brokerage: invalidated {} frozen commission row(s) for order {} (aftersale approved)", rows, orderSn);
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // Unfreeze (one transaction per row; called by BrokerageUnfreezeScheduler)
    // ------------------------------------------------------------------

    /**
     * Mature one frozen row: guarded 0→1 flip plus the {@code brokerage_price}
     * credit, atomically. A 0-row flip means a concurrent sweep or a clawback got
     * there first — skip, NEVER retry-credit. A failed credit (user vanished)
     * throws so the flip rolls back and the row is retried next sweep.
     */
    @Transactional
    public boolean unfreeze(LitemallUserBrokerageRecord record) {
        int flipped = recordMapper.markValidFromFrozen(record.getId());
        if (flipped == 0) {
            return false;
        }
        int credited = userMapper.creditBrokerage(record.getUserId(), record.getPrice());
        if (credited == 0) {
            throw new IllegalStateException("creditBrokerage matched no live user " + record.getUserId()
                    + " for brokerage record " + record.getId());
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Config
    // ------------------------------------------------------------------

    /** Fresh read of the four {@code litemall_brokerage_*} rows (never the static cache). */
    public BrokerageConfig loadConfig() {
        Map<String, String> all = systemConfigService.queryAll();
        boolean enabled = Boolean.parseBoolean(all.getOrDefault(KEY_ENABLED, "false"));
        BigDecimal rate = parseDecimal(all.get(KEY_RATE), new BigDecimal("5"), KEY_RATE);
        int freezeDays = (int) parseDecimal(all.get(KEY_FREEZE_DAYS), new BigDecimal("7"), KEY_FREEZE_DAYS).longValue();
        BigDecimal minExtract = parseDecimal(all.get(KEY_MIN_EXTRACT), new BigDecimal("10"), KEY_MIN_EXTRACT);
        return new BrokerageConfig(enabled, rate, freezeDays, minExtract);
    }

    private static BigDecimal parseDecimal(String raw, BigDecimal fallback, String key) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Unparseable {} value '{}'; using default {}", key, raw, fallback);
            return fallback;
        }
    }

    /** Snapshot of the brokerage config rows at one read. */
    public record BrokerageConfig(boolean enabled, BigDecimal rate, int freezeDays, BigDecimal minExtract) {
    }
}
