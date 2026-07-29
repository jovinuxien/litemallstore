package org.linlinjava.litemall.goods.application.inventoryflow;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.linlinjava.litemall.db.dao.LitemallRetireCandidateMapper;
import org.linlinjava.litemall.db.dao.LitemallSeckillMapper;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallRetireCandidate;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.application.search.SearchReindexService;
import org.linlinjava.litemall.goods.application.seo.SitemapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Wave-14 retirement executor: daily (default 02:00, before the 03:00 catalog cycle) flips
 * APPROVED retire candidates whose {@code execute_on} has arrived to OFF-SALE, reindexes each
 * goods (they drop out of search and deals; the sitemap already filters on-sale), and CAS-marks
 * the rows {@code executed}. The PDP stays viewable-unbuyable; reversal is the normal admin
 * on-sale toggle (the nightly promote no longer force-re-enables existing goods — Wave 14).
 *
 * <p>Honesty rules: a goods with a LIVE flash deal is skipped with a logged reason and stays
 * approved for the next run (the deal engine owns it until unwind); a vanished/deleted goods is
 * marked executed with a log (nothing left to flip). Never a fake success, never an exception
 * out of the tick.
 */
@Component
public class RetirementExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetirementExecutor.class);

    private final LitemallRetireCandidateMapper retireMapper;
    private final LitemallGoodsService goodsService;
    private final LitemallSeckillMapper seckillMapper;
    private final SearchReindexService reindexService;
    private final SitemapService sitemapService;
    private final CategoryInsightCache insightCache;

    public RetirementExecutor(LitemallRetireCandidateMapper retireMapper,
                              LitemallGoodsService goodsService,
                              LitemallSeckillMapper seckillMapper,
                              SearchReindexService reindexService,
                              SitemapService sitemapService,
                              CategoryInsightCache insightCache) {
        this.retireMapper = retireMapper;
        this.goodsService = goodsService;
        this.seckillMapper = seckillMapper;
        this.reindexService = reindexService;
        this.sitemapService = sitemapService;
        this.insightCache = insightCache;
    }

    @Scheduled(cron = "${litemall.inventoryflow.retire-cron:0 0 2 * * *}")
    public void tick() {
        try {
            execute(LocalDate.now());
        } catch (RuntimeException ex) {
            log.warn("retirement executor tick failed (next run retries): {}", ex.getMessage());
        }
    }

    /** Runs one execution pass; also the dev/admin manual trigger. Returns an honest summary. */
    public Map<String, Object> execute(LocalDate today) {
        List<LitemallRetireCandidate> due = retireMapper.selectDueApproved(today);
        int executed = 0;
        int skippedLiveDeal = 0;
        int goodsMissing = 0;
        int lostRace = 0;
        for (LitemallRetireCandidate candidate : due) {
            Integer goodsId = candidate.getGoodsId();
            try {
                if (seckillMapper.selectLiveByGoodsId(goodsId) != null) {
                    skippedLiveDeal++;
                    log.info("retirement: goods {} has a LIVE flash deal — skipped, stays approved "
                            + "for the next run", goodsId);
                    continue;
                }
                LitemallGoods goods = goodsService.findById(goodsId);
                if (goods == null) {
                    goodsMissing++;
                    log.info("retirement: goods {} no longer exists — marking executed", goodsId);
                } else if (Boolean.TRUE.equals(goods.getIsOnSale())) {
                    LitemallGoods offSale = new LitemallGoods();
                    offSale.setId(goodsId);
                    offSale.setIsOnSale(Boolean.FALSE);
                    goodsService.updateById(offSale);
                    reindexService.reindexGoods(goodsId); // off-sale ⇒ deleted from the index
                } else {
                    log.info("retirement: goods {} already off-sale — marking executed", goodsId);
                    reindexService.reindexGoods(goodsId);
                }
                if (retireMapper.updateStatus(candidate.getId(),
                        LitemallRetireCandidate.STATUS_APPROVED,
                        LitemallRetireCandidate.STATUS_EXECUTED, null) > 0) {
                    executed++;
                } else {
                    lostRace++;
                    log.warn("retirement: candidate {} (goods {}) changed status mid-run — "
                            + "not marked executed", candidate.getId(), goodsId);
                }
            } catch (RuntimeException ex) {
                log.warn("retirement: goods {} failed — stays approved for the next run: {}",
                        goodsId, ex.getMessage());
            }
        }
        if (executed > 0) {
            try {
                sitemapService.rebuild();
            } catch (RuntimeException seoEx) {
                log.warn("retirement: sitemap rebuild failed (nightly refresh will retry): {}",
                        seoEx.getMessage());
            }
            try {
                insightCache.refresh(true);
            } catch (RuntimeException cacheEx) {
                log.warn("retirement: insight cache refresh failed: {}", cacheEx.getMessage());
            }
        }
        if (!due.isEmpty()) {
            log.info("retirement executor: {} due — {} executed, {} live-deal skips, "
                            + "{} goods missing, {} lost races",
                    due.size(), executed, skippedLiveDeal, goodsMissing, lostRace);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("due", due.size());
        summary.put("executed", executed);
        summary.put("skippedLiveDeal", skippedLiveDeal);
        summary.put("goodsMissing", goodsMissing);
        summary.put("lostRace", lostRace);
        return summary;
    }
}
