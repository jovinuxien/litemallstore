package org.linlinjava.litemall.goods.application.deals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.linlinjava.litemall.db.dao.LitemallDealCandidateMapper;
import org.linlinjava.litemall.db.domain.LitemallDealCandidate;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.db.service.LitemallGoodsProductService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallDealsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Wave-14 auto daily deals (user decision 2026-07-29, supersedes Wave-12's manual-only rule):
 * every morning (default 05:00 — clear of the 02:00 retire / 02:45 related / 03:00 sync /
 * 03:30 enrich / 04:15 recheck chain) the top-N {@code hot}-tier PROPOSED deal candidates from
 * today ∪ yesterday become real flash deals through the existing {@link FlashDealService}
 * author path (same validations as an admin approve, including the CJ cost floor).
 *
 * <p>Hard rules: price = max(suggestedDealPrice, cost × 1.05) — an auto deal can never sell at
 * a loss; cap {@code auto-daily-cap} deals per tick; kill-switch
 * {@code litemall.deals.auto-daily-enabled} (env-overridable); DISMISSED candidates are never
 * touched (only {@code proposed} rows are read); cost-unknown goods are skipped with a logged
 * reason and never count against the cap. Created deals activate on the next lifecycle tick
 * (price swap + reindex), which lights the storefront /deals page via {@code deal_flag}.
 */
@Component
public class AutoDailyDealTask {

    private static final Logger log = LoggerFactory.getLogger(AutoDailyDealTask.class);

    private static final String TIER_HOT = "hot";
    private static final BigDecimal FLOOR_OVER_COST = new BigDecimal("1.05");

    private final LitemallDealCandidateMapper candidateMapper;
    private final FlashDealService flashDealService;
    private final LitemallGoodsService goodsService;
    private final LitemallGoodsProductService productService;
    private final LitemallDealsProperties properties;

    public AutoDailyDealTask(LitemallDealCandidateMapper candidateMapper,
                             FlashDealService flashDealService,
                             LitemallGoodsService goodsService,
                             LitemallGoodsProductService productService,
                             LitemallDealsProperties properties) {
        this.candidateMapper = candidateMapper;
        this.flashDealService = flashDealService;
        this.goodsService = goodsService;
        this.productService = productService;
        this.properties = properties;
    }

    @Scheduled(cron = "${litemall.deals.auto-daily-cron:0 0 5 * * *}")
    public void tick() {
        try {
            run(LocalDate.now(), LocalDateTime.now());
        } catch (RuntimeException ex) {
            log.warn("auto daily deals tick failed (next run retries): {}", ex.getMessage());
        }
    }

    /** One pass; parameterized clock for tests and the dev manual trigger. Honest summary out. */
    public Map<String, Object> run(LocalDate today, LocalDateTime now) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (!properties.isAutoDailyEnabled()) {
            log.info("auto daily deals: disabled by kill-switch — nothing created");
            summary.put("enabled", false);
            summary.put("created", 0);
            return summary;
        }
        List<LitemallDealCandidate> ranked = hotProposals(today);
        int cap = properties.getAutoDailyCap();
        int created = 0;
        int skipped = 0;
        for (LitemallDealCandidate candidate : ranked) {
            if (created >= cap) {
                break;
            }
            if (createDeal(candidate, now)) {
                created++;
            } else {
                skipped++;
            }
        }
        log.info("auto daily deals: {} hot proposals considered — {} created (cap {}), {} skipped",
                ranked.size(), created, cap, skipped);
        summary.put("enabled", true);
        summary.put("considered", ranked.size());
        summary.put("created", created);
        summary.put("skipped", skipped);
        summary.put("cap", cap);
        return summary;
    }

    /** Hot-tier PROPOSED candidates from today ∪ yesterday, best score first, one per goods. */
    private List<LitemallDealCandidate> hotProposals(LocalDate today) {
        List<LitemallDealCandidate> all = new ArrayList<>();
        all.addAll(candidateMapper.selectByDay(today, LitemallDealCandidate.STATUS_PROPOSED));
        all.addAll(candidateMapper.selectByDay(today.minusDays(1), LitemallDealCandidate.STATUS_PROPOSED));
        all.removeIf(c -> !TIER_HOT.equals(c.getTier()));
        all.sort((a, b) -> b.getScore().compareTo(a.getScore()));
        List<LitemallDealCandidate> deduped = new ArrayList<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (LitemallDealCandidate c : all) {
            if (seen.add(c.getGoodsId())) {
                deduped.add(c);
            }
        }
        return deduped;
    }

    /** Returns true only when a real deal row was created. Every skip is logged with its reason. */
    private boolean createDeal(LitemallDealCandidate candidate, LocalDateTime now) {
        Integer goodsId = candidate.getGoodsId();
        LitemallGoods goods = goodsService.findById(goodsId);
        if (goods == null || !Boolean.TRUE.equals(goods.getIsOnSale())) {
            log.info("auto daily deals: goods {} missing or off-sale — skipped", goodsId);
            return false;
        }
        BigDecimal cost = goods.getCost() != null && goods.getCost().signum() > 0 ? goods.getCost() : null;
        if (cost == null) {
            log.info("auto daily deals: goods {} has no captured cost — skipped (652 path), "
                    + "not counted against the cap", goodsId);
            return false;
        }
        BigDecimal floor = cost.multiply(FLOOR_OVER_COST).setScale(2, RoundingMode.HALF_UP);
        BigDecimal suggested = candidate.getSuggestedDealPrice();
        BigDecimal price = suggested != null ? suggested.max(floor) : floor;
        BigDecimal retail = goods.getRetailPrice();
        if (retail == null || price.compareTo(retail) >= 0) {
            log.info("auto daily deals: goods {} floor {} is not below retail {} — no honest "
                    + "markdown possible, skipped", goodsId, price, retail);
            return false;
        }
        int stockTotal = 0;
        for (LitemallGoodsProduct sku : productService.queryByGid(goodsId)) {
            if (sku.getNumber() != null) {
                stockTotal += sku.getNumber();
            }
        }
        int quota = Math.min(stockTotal, properties.getAutoDailyStockCap());
        if (quota <= 0) {
            log.info("auto daily deals: goods {} has no stock — skipped", goodsId);
            return false;
        }
        FlashDealService.Result result = flashDealService.create(goodsId, price, now,
                now.plusHours(properties.getAutoDailyWindowHours()), quota);
        if (result.errno() != null) {
            log.info("auto daily deals: goods {} refused by the author path (errno {}): {} — skipped",
                    goodsId, result.errno(), result.error());
            return false;
        }
        if (candidateMapper.updateStatus(candidate.getId(), LitemallDealCandidate.STATUS_PROPOSED,
                LitemallDealCandidate.STATUS_APPROVED) == 0) {
            log.warn("auto daily deals: candidate {} (goods {}) changed status mid-run — the deal "
                    + "stands, candidate left as it is", candidate.getId(), goodsId);
        }
        log.info("auto daily deals: goods {} → deal {} at {} ({}h window, quota {})",
                goodsId, result.deal().getId(), price, properties.getAutoDailyWindowHours(), quota);
        return true;
    }
}
