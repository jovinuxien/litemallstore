package org.linlinjava.litemall.goods.application.seo;

import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.application.search.SearchReindexService;
import org.linlinjava.litemall.goods.application.seo.KeywordResearchProvider.KeywordDemand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin surface for the over-length-title problem: which live products have titles Google will
 * truncate, what a shorter one would look like, and which bought search term backs each.
 *
 * <p>The first consumer of {@link KeywordResearchProvider}. It asks per CATEGORY, never per
 * product — the provider bills per seed when it is pointed at the live platform, and one
 * category's terms answer for every product in it.
 *
 * <p><strong>Nothing here rewrites a title on its own.</strong> {@link #scan} only reads;
 * {@link #apply} writes exactly the title an administrator submitted. Titles are the shop's
 * voice in search results, and the keyword data behind the suggestions carries known noise
 * (competitor brands, and homonyms like "friday night lights" landing under Night Lights) —
 * auto-applying it would publish that noise under the shop's name.
 */
@Service
public class TitleOptimisationService {

    private static final Logger log = LoggerFactory.getLogger(TitleOptimisationService.class);

    /** Same PK walk MetaCatalogFeedService and SitemapService use. */
    private static final int PAGE_SIZE = 500;

    /** Terms attached to each row as evidence — enough to choose from, not a data dump. */
    private static final int TERMS_PER_ROW = 6;

    /** A title longer than this is rejected outright; the column is varchar(127). */
    private static final int TITLE_COLUMN_LIMIT = 127;

    private final LitemallGoodsService goodsService;
    private final LitemallCategoryService categoryService;
    private final KeywordResearchProvider keywords;
    private final SearchReindexService reindexService;

    public TitleOptimisationService(LitemallGoodsService goodsService,
                                    LitemallCategoryService categoryService,
                                    KeywordResearchProvider keywords,
                                    SearchReindexService reindexService) {
        this.goodsService = goodsService;
        this.categoryService = categoryService;
        this.keywords = keywords;
        this.reindexService = reindexService;
    }

    /**
     * Live products whose title exceeds {@code maxLength}, with a proposal for each.
     *
     * <p>Read-only. Off-sale and deleted rows are skipped: a title nobody can reach is not worth
     * an administrator's attention.
     *
     * @param categoryId optional filter; null scans the whole catalogue
     */
    public Page scan(int maxLength, Integer categoryId, int page, int limit) {
        int max = maxLength > 0 ? maxLength : TitleProposer.DEFAULT_MAX_LENGTH;
        Map<Integer, String> categoryNames = new HashMap<>();
        // Terms are fetched once per category, not once per product — the difference between
        // one provider call and one per row.
        Map<String, List<KeywordDemand>> termsByCategory = new HashMap<>();

        List<Row> hits = new ArrayList<>();
        int scanned = 0;
        int pk = 1;
        while (true) {
            List<LitemallGoods> batch =
                    goodsService.querySelective(null, null, null, pk, PAGE_SIZE, "id", "asc");
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (LitemallGoods goods : batch) {
                if (goods.getId() == null || !Boolean.TRUE.equals(goods.getIsOnSale())) {
                    continue;
                }
                if (categoryId != null && !categoryId.equals(goods.getCategoryId())) {
                    continue;
                }
                scanned++;
                String category = categoryNames.computeIfAbsent(
                        goods.getCategoryId(), this::categoryName);
                List<KeywordDemand> terms = termsByCategory.computeIfAbsent(
                        category == null ? "" : category,
                        c -> c.isEmpty() ? List.of() : keywords.demandFor(c, TERMS_PER_ROW));

                TitleProposer.Proposal proposal =
                        TitleProposer.propose(goods.getName(), terms, max);
                if (proposal == null || !proposal.overLength()) {
                    continue;
                }
                hits.add(new Row(goods.getId(), category, proposal, terms));
            }
            if (batch.size() < PAGE_SIZE) {
                break;
            }
            pk++;
        }

        // Longest first: the worst offenders are where the attention pays off.
        hits.sort((a, b) -> Integer.compare(b.proposal().currentLength(),
                a.proposal().currentLength()));

        int from = Math.max(0, (Math.max(1, page) - 1) * Math.max(1, limit));
        int to = Math.min(hits.size(), from + Math.max(1, limit));
        List<Row> slice = from >= hits.size() ? List.of() : hits.subList(from, to);
        return new Page(hits.size(), scanned, max, List.copyOf(slice));
    }

    /** A batch larger than this is refused outright; each row is a MySQL write plus an OCS call. */
    public static final int BATCH_LIMIT = 100;

    /**
     * Write one administrator-chosen title.
     *
     * <p>Reindexes the row afterwards for the same reason {@code CatalogHygieneService} does:
     * the name lives in the OCS document too, and a title changed only in MySQL would leave the
     * storefront's search results quoting the old one.
     *
     * <p>The reindex is reported, never thrown. The name is already written by the time the
     * indexer is called, so an exception here would tell the administrator the apply FAILED
     * while MySQL says it succeeded — the same "worked but said it failed" shape the first live
     * use of this page hit one layer up. Instead {@link Applied#reindexed()} is false and the
     * cause rides along, so the caller can say "saved; on-site search still shows the old
     * title" and mean it.
     *
     * <p>{@code goods.keywords} is deliberately left alone. Despite the name it is a SEARCH
     * column ({@code LitemallGoodsService.querySelective} LIKE-matches it), so the old longer
     * text sitting there keeps search recall the shorter title would otherwise lose.
     *
     * @throws IllegalArgumentException on a missing goods or an unusable title; nothing has
     *                                  been written when this is thrown
     */
    public Applied apply(Integer goodsId, String newTitle) {
        if (goodsId == null) {
            throw new IllegalArgumentException("goodsId is required");
        }
        String title = HtmlText.clean(newTitle);
        if (title.isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (title.length() > TITLE_COLUMN_LIMIT) {
            throw new IllegalArgumentException(
                    "title must be at most " + TITLE_COLUMN_LIMIT + " characters");
        }
        LitemallGoods goods = goodsService.findById(goodsId);
        if (goods == null) {
            throw new IllegalArgumentException("no such goods: " + goodsId);
        }
        if (title.equals(goods.getName())) {
            return new Applied(goodsId, title, false, true, null);
        }

        LitemallGoods patch = new LitemallGoods();
        patch.setId(goodsId);
        patch.setName(title);
        goodsService.updateById(patch);
        log.info("seo title: goods {} retitled ('{}' -> '{}')", goodsId, goods.getName(), title);
        try {
            reindexService.reindexGoods(goodsId);
        } catch (RuntimeException e) {
            log.warn("seo title: goods {} retitled in MySQL but NOT reindexed: {}",
                    goodsId, e.toString());
            return new Applied(goodsId, title, true, false, e.getMessage() == null
                    ? e.getClass().getSimpleName() : e.getMessage());
        }
        return new Applied(goodsId, title, true, true, null);
    }

    /**
     * Apply several administrator-chosen titles in one call.
     *
     * <p>Each row goes through {@link #apply} on its own: a refused or failed row is reported in
     * its place and the batch carries on — one bad title must not hold the other ninety-nine
     * hostage, and nothing already written can be taken back anyway. Rows are applied in the
     * order given; a duplicate goodsId is applied twice, last title wins, which is what the
     * caller asked for.
     *
     * @throws IllegalArgumentException when the batch is empty or over {@link #BATCH_LIMIT};
     *                                  nothing has been written when this is thrown
     */
    public List<BatchResult> applyBatch(List<TitleChange> changes) {
        if (changes == null || changes.isEmpty()) {
            throw new IllegalArgumentException("nothing to apply");
        }
        if (changes.size() > BATCH_LIMIT) {
            throw new IllegalArgumentException(
                    "at most " + BATCH_LIMIT + " titles per batch (" + changes.size() + " sent)");
        }
        List<BatchResult> results = new ArrayList<>(changes.size());
        for (TitleChange change : changes) {
            Integer goodsId = change == null ? null : change.goodsId();
            try {
                Applied applied = apply(goodsId, change == null ? null : change.title());
                results.add(new BatchResult(goodsId, true, applied.title(), applied.changed(),
                        applied.reindexed(), applied.reindexError()));
            } catch (IllegalArgumentException e) {
                results.add(new BatchResult(goodsId, false, null, false, false, e.getMessage()));
            }
        }
        return results;
    }

    /** One requested change: the title exactly as the administrator submitted it. */
    public record TitleChange(Integer goodsId, String title) {
    }

    /**
     * @param changed      false when the submitted title already was the live one (a no-op)
     * @param reindexed    false when MySQL was written but the OCS document was not refreshed
     * @param reindexError why, when {@code reindexed} is false; null otherwise
     */
    public record Applied(Integer goodsId, String title, boolean changed, boolean reindexed,
                          String reindexError) {
    }

    /**
     * @param ok    false when the row was refused (bad title, unknown goods) — nothing written
     * @param error the refusal, or the reindex failure when {@code ok} but not {@code reindexed}
     */
    public record BatchResult(Integer goodsId, boolean ok, String title, boolean changed,
                              boolean reindexed, String error) {
    }

    private String categoryName(Integer id) {
        if (id == null || id <= 0) {
            return null;
        }
        LitemallCategory category = categoryService.findById(id);
        return category != null && !Boolean.TRUE.equals(category.getDeleted())
                ? category.getName() : null;
    }

    /**
     * @param total   over-length products matching the filter, across all pages
     * @param scanned live products examined — the denominator for "how bad is it"
     */
    public record Page(int total, int scanned, int maxLength, List<Row> rows) {
    }

    /** @param terms the category's demand evidence, so the editor is not guessing */
    public record Row(Integer goodsId, String category, TitleProposer.Proposal proposal,
                      List<KeywordDemand> terms) {
    }
}
