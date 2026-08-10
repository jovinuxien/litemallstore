package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.application.seo.MetaCatalogFeedService;
import org.linlinjava.litemall.goods.application.seo.SitemapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wave-25 catalog hygiene, run on demand from the admin surface (idempotent — re-runs are
 * no-ops once the catalog is clean):
 *
 * <ul>
 *   <li><b>Units:</b> CJK unit glyphs ({@code 件}/{@code 盒}/…, seeded by the old adapter default
 *       and upstream data) render beside the € price on the PDP — normalized to blank. The
 *       adapter no longer stamps them on new promotes; this pass cleans the existing rows.</li>
 *   <li><b>Chinese-named on-sale goods</b> poison merchant-feed review: renamed from their CJ
 *       snapshot's English title when it is clean, else OFF-SALED (reversible, the standard
 *       retirement semantics) with the reason logged. Renames/off-sales propagate per row to the
 *       OCS index, and the sitemap + feed artifacts are rebuilt at the end (the slugged links
 *       derive from the name).</li>
 * </ul>
 *
 * <p>Returns honest counts; a per-row failure is logged and skipped so a single bad row can't
 * abort the pass (that row stays dirty and is retried on the next run).
 */
@Service
public class CatalogHygieneService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogHygieneService.class);

    private static final int PAGE_SIZE = 200;
    private static final int NAME_MAX = 127;

    private final LitemallGoodsService goodsService;
    private final LitemallCjProductService cjProductStore;
    private final SearchReindexService reindexService;
    private final SitemapService sitemapService;
    private final MetaCatalogFeedService metaCatalogFeedService;

    public CatalogHygieneService(LitemallGoodsService goodsService,
                                 LitemallCjProductService cjProductStore,
                                 SearchReindexService reindexService,
                                 SitemapService sitemapService,
                                 MetaCatalogFeedService metaCatalogFeedService) {
        this.goodsService = goodsService;
        this.cjProductStore = cjProductStore;
        this.reindexService = reindexService;
        this.sitemapService = sitemapService;
        this.metaCatalogFeedService = metaCatalogFeedService;
    }

    /** Full hygiene pass; returns {scanned, unitsNormalized, renamed, offSaled, failed}. */
    public Map<String, Object> run() {
        int scanned = 0;
        int unitsNormalized = 0;
        int renamed = 0;
        int offSaled = 0;
        int failed = 0;

        int page = 1;
        while (true) {
            List<LitemallGoods> batch = goodsService.querySelective(
                    null, null, null, page, PAGE_SIZE, "id", "asc");
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (LitemallGoods goods : batch) {
                if (goods.getId() == null) {
                    continue;
                }
                scanned++;
                try {
                    Outcome outcome = fixOne(goods);
                    unitsNormalized += outcome.unitNormalized ? 1 : 0;
                    renamed += outcome.renamed ? 1 : 0;
                    offSaled += outcome.offSaled ? 1 : 0;
                } catch (RuntimeException ex) {
                    failed++;
                    LOGGER.warn("catalog hygiene failed for goods {} (skipped, retried next run): {}",
                            goods.getId(), ex.getMessage());
                }
            }
            if (batch.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }

        if (renamed > 0 || offSaled > 0) {
            try {
                sitemapService.rebuild();
                metaCatalogFeedService.rebuild();
            } catch (RuntimeException ex) {
                LOGGER.warn("catalog hygiene: sitemap/feed rebuild failed (next nightly covers it): {}",
                        ex.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanned", scanned);
        result.put("unitsNormalized", unitsNormalized);
        result.put("renamed", renamed);
        result.put("offSaled", offSaled);
        result.put("failed", failed);
        LOGGER.info("catalog hygiene: {}", result);
        return result;
    }

    private record Outcome(boolean unitNormalized, boolean renamed, boolean offSaled) {
    }

    private Outcome fixOne(LitemallGoods goods) {
        LitemallGoods patch = new LitemallGoods();
        patch.setId(goods.getId());
        boolean unitNormalized = false;
        boolean nameRenamed = false;
        boolean offSaled = false;

        if (containsHan(goods.getUnit())) {
            patch.setUnit("");
            unitNormalized = true;
        }

        // Chinese names only matter while the product is customer-visible; off-sale rows keep
        // their name (reversible retirement semantics) until they ever come back.
        if (Boolean.TRUE.equals(goods.getIsOnSale()) && containsHan(goods.getName())) {
            String replacement = englishTitleFromSnapshot(goods);
            if (replacement != null) {
                patch.setName(replacement);
                patch.setKeywords(replacement);
                nameRenamed = true;
                LOGGER.info("catalog hygiene: goods {} renamed from CJ snapshot title ('{}' -> '{}')",
                        goods.getId(), goods.getName(), replacement);
            } else {
                patch.setIsOnSale(Boolean.FALSE);
                offSaled = true;
                LOGGER.info("catalog hygiene: goods {} off-saled — Chinese name '{}' with no clean "
                        + "English snapshot title to rename from", goods.getId(), goods.getName());
            }
        }

        if (!unitNormalized && !nameRenamed && !offSaled) {
            return new Outcome(false, false, false);
        }
        goodsService.updateById(patch);
        // Name and on-sale state live in the OCS document — converge it row by row (an off-sale
        // flip deletes the document; a plain unit fix reindexes harmlessly).
        reindexService.reindexGoods(goods.getId());
        return new Outcome(unitNormalized, nameRenamed, offSaled);
    }

    /** The CJ snapshot's (English) title, cleaned for use as the goods name; null when unusable. */
    private String englishTitleFromSnapshot(LitemallGoods goods) {
        if (goods.getCjPid() == null || goods.getCjPid().isBlank()) {
            return null;
        }
        LitemallCjProduct snapshot = cjProductStore.findByPid(goods.getCjPid());
        if (snapshot == null || snapshot.getTitle() == null) {
            return null;
        }
        String title = snapshot.getTitle().trim();
        if (title.isEmpty() || containsHan(title) || title.equals(goods.getName())) {
            return null;
        }
        return title.length() <= NAME_MAX ? title : title.substring(0, NAME_MAX);
    }

    static boolean containsHan(String value) {
        return value != null && value.codePoints()
                .anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }
}
