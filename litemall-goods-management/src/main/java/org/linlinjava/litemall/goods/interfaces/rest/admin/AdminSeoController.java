package org.linlinjava.litemall.goods.interfaces.rest.admin;

import jakarta.validation.constraints.NotNull;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.seo.TitleOptimisationService;
import org.linlinjava.litemall.goods.application.seo.TitleProposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * On-page SEO admin surface ({@code /srv/private/admin/seo/**} — machine token + ROLE_ADMIN via
 * the svcsecurity default admin path, and it rides gateway-admin's {@code /srv/**} catch-all,
 * so no gateway route change is needed).
 *
 * <p>Serves the over-length-title worklist for the admin SPA: which live products carry titles
 * Google will truncate, a shorter proposal for each, and the bought search terms for that
 * product's category as evidence.
 *
 * <p>Errnos: 660 = the submitted title is unusable (blank, too long) or the goods does not
 * exist. Nothing here is a paid call — the keyword side reads a local export by default.
 */
@RestController
@RequestMapping("/srv/private/admin/seo")
@Validated
public class AdminSeoController {

    private static final Logger log = LoggerFactory.getLogger(AdminSeoController.class);

    /** Submitted title unusable, or no such goods. */
    private static final int ERRNO_BAD_TITLE = 660;

    private final TitleOptimisationService titles;

    public AdminSeoController(TitleOptimisationService titles) {
        this.titles = titles;
    }

    /**
     * The worklist. Read-only and idempotent.
     *
     * @param maxLength title length to judge against; defaults to the ~60 characters Google
     *                  renders before truncating
     */
    @GetMapping("/titles")
    public Object titles(@RequestParam(defaultValue = "60") Integer maxLength,
                         @RequestParam(required = false) Integer categoryId,
                         @RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "20") Integer limit) {
        TitleOptimisationService.Page result = titles.scan(
                maxLength == null ? TitleProposer.DEFAULT_MAX_LENGTH : maxLength,
                categoryId,
                page == null ? 1 : page,
                limit == null ? 20 : limit);

        List<Map<String, Object>> rows = new ArrayList<>(result.rows().size());
        for (TitleOptimisationService.Row row : result.rows()) {
            TitleProposer.Proposal p = row.proposal();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("goodsId", row.goodsId());
            item.put("category", row.category());
            item.put("currentTitle", p.currentTitle());
            item.put("currentLength", p.currentLength());
            item.put("proposedTitle", p.proposedTitle());
            item.put("proposedLength", p.proposedLength());
            // True when truncation ate the matched keyword or the meaning — the SPA badges
            // these so an administrator does not bulk-apply them unread.
            item.put("needsReview", p.needsReview());
            item.put("matchedKeyword", p.matchedKeyword());
            item.put("matchedKeywordVolume", p.matchedKeywordVolume());
            item.put("keywordSurvives", p.keywordSurvives());
            List<Map<String, Object>> terms = new ArrayList<>();
            row.terms().forEach(t -> {
                Map<String, Object> term = new LinkedHashMap<>();
                term.put("term", t.term());
                // null means UNKNOWN, never 0 — the SPA renders it as "—".
                term.put("monthlySearches", t.monthlySearches());
                terms.add(term);
            });
            item.put("terms", terms);
            rows.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", result.total());
        data.put("scanned", result.scanned());
        data.put("maxLength", result.maxLength());
        data.put("list", rows);
        return ResponseUtil.ok(data);
    }

    /**
     * Apply one administrator-chosen title.
     *
     * <p>Takes the title from the body rather than regenerating it: the SPA lets the editor
     * amend the proposal, and applying anything other than what they read and approved would
     * make the confirmation dialog a lie.
     *
     * <p>Response data: {@code {goodsId, title, changed, reindexed, warning?}}. {@code reindexed}
     * is false when MySQL holds the new title but the OCS document could not be refreshed — the
     * call is still errno 0, because the apply DID happen; the SPA shows the warning so the
     * administrator knows on-site search is stale rather than retrying a write that already
     * landed.
     */
    @PostMapping("/titles/apply")
    public Object applyTitle(@RequestBody @NotNull TitleUpdate body) {
        try {
            TitleOptimisationService.Applied applied = titles.apply(body.goodsId(), body.title());
            return ResponseUtil.ok(appliedData(applied));
        } catch (IllegalArgumentException e) {
            log.warn("seo title apply refused for goods {}: {}", body.goodsId(), e.getMessage());
            return ResponseUtil.fail(ERRNO_BAD_TITLE, e.getMessage());
        }
    }

    /**
     * Apply up to {@link TitleOptimisationService#BATCH_LIMIT} titles in one call.
     *
     * <p>Body {@code {items:[{goodsId, title}]}}. Response data
     * {@code {applied, failed, results:[{goodsId, ok, title?, changed, reindexed, error?}]}}
     * — one entry per submitted item, in order. A refused row is reported in place; the rest of
     * the batch still runs. Only an empty or over-limit batch is refused as a whole (errno 660),
     * and in that case nothing has been written.
     */
    @PostMapping("/titles/apply-batch")
    public Object applyTitles(@RequestBody @NotNull TitleBatch body) {
        List<TitleOptimisationService.TitleChange> changes = new ArrayList<>();
        if (body.items() != null) {
            for (TitleUpdate item : body.items()) {
                changes.add(new TitleOptimisationService.TitleChange(
                        item == null ? null : item.goodsId(), item == null ? null : item.title()));
            }
        }
        List<TitleOptimisationService.BatchResult> results;
        try {
            results = titles.applyBatch(changes);
        } catch (IllegalArgumentException e) {
            log.warn("seo title batch refused: {}", e.getMessage());
            return ResponseUtil.fail(ERRNO_BAD_TITLE, e.getMessage());
        }

        int applied = 0;
        int failed = 0;
        List<Map<String, Object>> rows = new ArrayList<>(results.size());
        for (TitleOptimisationService.BatchResult r : results) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("goodsId", r.goodsId());
            row.put("ok", r.ok());
            row.put("title", r.title());
            row.put("changed", r.changed());
            row.put("reindexed", r.reindexed());
            row.put("error", r.error());
            rows.add(row);
            if (r.ok()) {
                applied++;
            } else {
                failed++;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("applied", applied);
        data.put("failed", failed);
        data.put("results", rows);
        log.info("seo title batch: {} applied, {} refused of {}", applied, failed, results.size());
        return ResponseUtil.ok(data);
    }

    private static Map<String, Object> appliedData(TitleOptimisationService.Applied applied) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("goodsId", applied.goodsId());
        data.put("title", applied.title());
        data.put("changed", applied.changed());
        data.put("reindexed", applied.reindexed());
        if (!applied.reindexed()) {
            data.put("warning", "Saved, but on-site search still shows the old title"
                    + (applied.reindexError() == null ? "" : ": " + applied.reindexError()));
        }
        return data;
    }

    public record TitleUpdate(Integer goodsId, String title) {
    }

    public record TitleBatch(List<TitleUpdate> items) {
    }
}
