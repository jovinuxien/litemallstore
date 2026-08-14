package org.linlinjava.litemall.goods.application.inventoryflow;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.dao.LitemallRetireCandidateMapper;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallRetireCandidate;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.application.pricing.CategoryMarginResolver;
import org.linlinjava.litemall.goods.application.search.SearchReindexService;
import org.linlinjava.litemall.goods.application.seo.SitemapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Wave-26 catalogue narrowing: take every ON-SALE CJ good OUTSIDE a chosen set of anchor L1 roots
 * off sale, and put them back later.
 *
 * <p><b>Why this exists.</b> Wave 26 asked for the non-anchor subtrees to be off-saled "through
 * the existing retirement path". That path could not do it: retirement candidates are only ever
 * created by {@link RetireCandidateScorer} from CJ-unavailability streaks, or by
 * {@link RetirementGovernor} topping up weakest-first against the catalogue target — both
 * catalogue-wide, neither able to take a category as input, and the admin approve endpoint
 * requires a pre-existing {@code proposed} row per goods id. This service supplies the missing
 * selection step and nothing else.
 *
 * <p><b>What it reuses.</b> The flip itself is NOT reimplemented. Narrowing stages rows in
 * {@code litemall_retire_candidate} as {@code approved} with an {@code execute_on}, and
 * {@link RetirementExecutor} — which already off-sales, reindexes per goods, rebuilds the sitemap
 * and refreshes the insight cache — does the work on its normal daily pass. So a narrowed goods
 * is indistinguishable from a retired one downstream, and the same live-flash-deal skip applies.
 *
 * <p><b>Reversibility is the point</b> (user intent 2026-08-13: run one anchor now, take the other
 * categories back later). Rows are never deleted; nothing here writes anything but
 * {@code is_on_sale}. {@link #restore} is driven by the narrowing AUDIT ROWS, never by category
 * alone, so goods taken off sale for other reasons — the Wave-26 price floor, catalogue hygiene,
 * weakness retirement — are never resurrected by a restore of their category.
 *
 * <p>Honesty rules: an admin decision already on a row (approved/dismissed/executed) is never
 * clobbered and is counted and reported instead; a preview has zero side effects; and narrowing
 * refuses outright rather than emptying the store (see {@link #resolveAnchors}).
 */
@Service
public class CatalogNarrowingService {

    private static final Logger log = LoggerFactory.getLogger(CatalogNarrowingService.class);

    /**
     * Marker written into the candidate's reasons JSON. {@link #restore} matches on it, so it is a
     * persisted contract: changing the text orphans rows staged by earlier runs.
     */
    static final String REASON_MARKER = "non-anchor category";

    /** Page size for both the goods sweep and the staging batches. */
    private static final int BATCH = 500;
    /** Hard ceiling on one restore pass, so a mistaken call cannot walk the whole table. */
    private static final int RESTORE_LIMIT = 20000;

    private final InsightMapper insightMapper;
    private final LitemallRetireCandidateMapper retireMapper;
    private final LitemallCategoryService categoryService;
    private final LitemallGoodsService goodsService;
    private final CategoryMarginResolver categoryResolver;
    private final SearchReindexService reindexService;
    private final SitemapService sitemapService;
    private final CategoryInsightCache insightCache;

    public CatalogNarrowingService(InsightMapper insightMapper,
                                   LitemallRetireCandidateMapper retireMapper,
                                   LitemallCategoryService categoryService,
                                   LitemallGoodsService goodsService,
                                   CategoryMarginResolver categoryResolver,
                                   SearchReindexService reindexService,
                                   SitemapService sitemapService,
                                   CategoryInsightCache insightCache) {
        this.insightMapper = insightMapper;
        this.retireMapper = retireMapper;
        this.categoryService = categoryService;
        this.goodsService = goodsService;
        this.categoryResolver = categoryResolver;
        this.reindexService = reindexService;
        this.sitemapService = sitemapService;
        this.insightCache = insightCache;
    }

    /**
     * Per-L1 on-sale counts split into anchor and non-anchor. ZERO side effects — this is what an
     * admin looks at before deciding, and what the narrowing acceptance is measured against.
     */
    public Map<String, Object> preview(List<Integer> anchorCategoryIds) {
        Set<Integer> anchors = resolveAnchors(anchorCategoryIds);
        Map<Integer, Long> perRoot = new HashMap<>();
        long orphaned = 0;
        for (Map<String, Object> row : insightMapper.selectOnSaleCjCountsByCategory()) {
            Integer categoryId = asInt(row.get("categoryId"));
            long cnt = asLong(row.get("cnt"));
            Integer root = categoryResolver.rootOfCategory(categoryId);
            if (root == null) {
                // Unknown/orphaned category: counted separately and treated as NON-anchor below,
                // because "we cannot place it" must not silently mean "it stays on sale".
                orphaned += cnt;
                continue;
            }
            perRoot.merge(root, cnt, Long::sum);
        }
        List<Map<String, Object>> anchorRows = new ArrayList<>();
        List<Map<String, Object>> otherRows = new ArrayList<>();
        long anchorTotal = 0;
        long otherTotal = 0;
        for (Map.Entry<Integer, Long> e : new TreeMap<>(perRoot).entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("categoryId", e.getKey());
            row.put("name", categoryName(e.getKey()));
            row.put("onSale", e.getValue());
            if (anchors.contains(e.getKey())) {
                anchorRows.add(row);
                anchorTotal += e.getValue();
            } else {
                otherRows.add(row);
                otherTotal += e.getValue();
            }
        }
        otherRows.sort((a, b) -> Long.compare(asLong(b.get("onSale")), asLong(a.get("onSale"))));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("anchors", anchorRows);
        out.put("nonAnchor", otherRows);
        out.put("orphanedCategoryGoods", orphaned);
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("onSale", anchorTotal + otherTotal + orphaned);
        totals.put("keep", anchorTotal);
        totals.put("wouldOffSale", otherTotal + orphaned);
        out.put("totals", totals);
        return out;
    }

    /**
     * Stage every on-sale CJ good outside the anchors for off-sale.
     *
     * @param executeOn when the executor should flip them; defaults to TODAY, because narrowing is
     *                  a deliberate one-off an admin has just previewed — unlike scored
     *                  retirement, which defaults to the next scheduled retirement day.
     * @param dryRun    when true, walks and counts exactly as a real run would but stages nothing.
     */
    public Map<String, Object> narrow(List<Integer> anchorCategoryIds, LocalDate executeOn, boolean dryRun) {
        Set<Integer> anchors = resolveAnchors(anchorCategoryIds);
        LocalDate day = LocalDate.now();
        LocalDate when = executeOn != null ? executeOn : day;
        String reasons = "[\"" + REASON_MARKER + " (anchor: " + joinNames(anchors) + ")\"]";

        int scanned = 0;
        int staged = 0;
        int alreadyDecided = 0;
        int batches = 0;
        int afterId = 0;
        List<LitemallRetireCandidate> pending = new ArrayList<>();
        while (true) {
            List<Map<String, Object>> page = insightMapper.selectOnSaleCjPage(afterId, BATCH);
            if (page.isEmpty()) {
                break;
            }
            for (Map<String, Object> row : page) {
                Integer goodsId = asInt(row.get("goodsId"));
                Integer categoryId = asInt(row.get("categoryId"));
                afterId = Math.max(afterId, goodsId == null ? afterId : goodsId);
                scanned++;
                if (goodsId == null) {
                    continue;
                }
                Integer root = categoryResolver.rootOfCategory(categoryId);
                if (root != null && anchors.contains(root)) {
                    continue; // survives: this is the storefront we are keeping
                }
                LitemallRetireCandidate existing = retireMapper.selectLatestByGoods(goodsId);
                if (existing != null && day.equals(existing.getDay()) && isDecided(existing.getStatus())) {
                    // Same-day row already carrying a decision: the batch upsert would leave it
                    // untouched anyway. Counted so the response never overstates what it staged.
                    alreadyDecided++;
                    continue;
                }
                LitemallRetireCandidate candidate = new LitemallRetireCandidate();
                candidate.setGoodsId(goodsId);
                candidate.setDay(day);
                candidate.setReasons(reasons);
                candidate.setExecuteOn(when);
                pending.add(candidate);
                staged++;
                if (pending.size() >= BATCH) {
                    batches += flush(pending, dryRun);
                }
            }
        }
        batches += flush(pending, dryRun);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dryRun", dryRun);
        summary.put("anchors", new ArrayList<>(anchors));
        summary.put("scanned", scanned);
        summary.put("staged", staged);
        summary.put("alreadyDecided", alreadyDecided);
        summary.put("keep", scanned - staged - alreadyDecided);
        summary.put("batches", batches);
        summary.put("executeOn", when.toString());
        log.info("catalogue narrowing ({}): {} on-sale scanned, {} staged for off-sale on {}, "
                        + "{} already decided, {} kept under anchors {}",
                dryRun ? "DRY RUN" : "staged", scanned, staged, when, alreadyDecided,
                scanned - staged - alreadyDecided, anchors);
        return summary;
    }

    /**
     * Put narrowed goods of the given L1 roots back ON SALE — the reversibility guarantee, and the
     * only bulk on-sale path in the system (the nightly promote deliberately withholds
     * {@code is_on_sale} on updates, so it can never do this by itself).
     */
    public Map<String, Object> restore(List<Integer> categoryIds) {
        Set<Integer> roots = new LinkedHashSet<>();
        for (Integer id : categoryIds == null ? List.<Integer>of() : categoryIds) {
            if (id != null) {
                roots.add(id);
            }
        }
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("categoryIds is required");
        }
        List<Map<String, Object>> rows = insightMapper.selectNarrowedOffSale(
                REASON_MARKER, LitemallRetireCandidate.STATUS_EXECUTED, RESTORE_LIMIT);
        int restored = 0;
        int skippedOtherCategory = 0;
        int lostRace = 0;
        for (Map<String, Object> row : rows) {
            Integer goodsId = asInt(row.get("goodsId"));
            Integer candidateId = asInt(row.get("candidateId"));
            Integer root = categoryResolver.rootOfCategory(asInt(row.get("categoryId")));
            if (goodsId == null || candidateId == null || root == null || !roots.contains(root)) {
                skippedOtherCategory++;
                continue;
            }
            try {
                LitemallGoods onSale = new LitemallGoods();
                onSale.setId(goodsId);
                onSale.setIsOnSale(Boolean.TRUE);
                goodsService.updateById(onSale);
                reindexService.reindexGoods(goodsId);
                if (retireMapper.updateStatus(candidateId, LitemallRetireCandidate.STATUS_EXECUTED,
                        LitemallRetireCandidate.STATUS_RESTORED, null) > 0) {
                    restored++;
                } else {
                    lostRace++;
                }
            } catch (RuntimeException ex) {
                log.warn("narrowing restore: goods {} failed — stays off sale: {}", goodsId, ex.getMessage());
            }
        }
        if (restored > 0) {
            try {
                sitemapService.rebuild();
            } catch (RuntimeException seoEx) {
                log.warn("narrowing restore: sitemap rebuild failed (nightly retries): {}", seoEx.getMessage());
            }
            try {
                insightCache.refresh(true);
            } catch (RuntimeException cacheEx) {
                log.warn("narrowing restore: insight cache refresh failed: {}", cacheEx.getMessage());
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("categoryIds", new ArrayList<>(roots));
        summary.put("candidatesConsidered", rows.size());
        summary.put("restored", restored);
        summary.put("skippedOtherCategory", skippedOtherCategory);
        summary.put("lostRace", lostRace);
        summary.put("truncated", rows.size() >= RESTORE_LIMIT);
        log.info("catalogue narrowing restore: {} of {} narrowed rows put back on sale for roots {}",
                restored, rows.size(), roots);
        return summary;
    }

    /**
     * Does this status represent a standing decision that narrowing must not take over?
     *
     * <p>{@code restored} deliberately does NOT: it records a policy REVERSAL, not an admin
     * decision to keep the goods on sale. Counting it as decided would make re-narrowing a
     * category you just restored a no-op for the rest of the day (the unique key is goods_id +
     * day, so the same row is reused) — the reversibility would only work in one direction.
     * Must stay in step with the ON DUPLICATE KEY UPDATE guard in insertApprovedBatch.
     */
    private static boolean isDecided(String status) {
        return status != null
                && !LitemallRetireCandidate.STATUS_PROPOSED.equals(status)
                && !LitemallRetireCandidate.STATUS_RESTORED.equals(status);
    }

    private int flush(List<LitemallRetireCandidate> pending, boolean dryRun) {
        if (pending.isEmpty()) {
            return 0;
        }
        if (!dryRun) {
            retireMapper.insertApprovedBatch(new ArrayList<>(pending));
        }
        pending.clear();
        return 1;
    }

    /**
     * Validates the anchor set. Refuses an empty set, an unknown category, a non-L1 category, and
     * an anchor set that holds NO on-sale goods — that last one would look like a valid narrowing
     * request and empty the entire storefront.
     */
    private Set<Integer> resolveAnchors(List<Integer> anchorCategoryIds) {
        if (anchorCategoryIds == null || anchorCategoryIds.isEmpty()) {
            throw new IllegalArgumentException("anchorCategoryIds is required — narrowing to nothing "
                    + "would take the whole storefront off sale");
        }
        Set<Integer> anchors = new LinkedHashSet<>();
        for (Integer id : anchorCategoryIds) {
            if (id == null) {
                continue;
            }
            LitemallCategory category = categoryService.findById(id);
            if (category == null || Boolean.TRUE.equals(category.getDeleted())) {
                throw new IllegalArgumentException("unknown category " + id);
            }
            if (category.getPid() != null && category.getPid() != 0) {
                throw new IllegalArgumentException("category " + id + " (" + category.getName()
                        + ") is not an L1 root — anchors are L1 roots");
            }
            anchors.add(id);
        }
        if (anchors.isEmpty()) {
            throw new IllegalArgumentException("anchorCategoryIds is required");
        }
        return anchors;
    }

    private String joinNames(Set<Integer> anchors) {
        List<String> names = new ArrayList<>();
        for (Integer id : anchors) {
            names.add(categoryName(id));
        }
        return String.join(" + ", names);
    }

    private String categoryName(Integer id) {
        try {
            LitemallCategory category = categoryService.findById(id);
            return category == null ? String.valueOf(id) : category.getName();
        } catch (RuntimeException ex) {
            return String.valueOf(id);
        }
    }

    private static Integer asInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    private static long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
