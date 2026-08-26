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
     */
    @PostMapping("/titles/apply")
    public Object applyTitle(@RequestBody @NotNull TitleUpdate body) {
        try {
            String applied = titles.apply(body.goodsId(), body.title());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("goodsId", body.goodsId());
            data.put("title", applied);
            return ResponseUtil.ok(data);
        } catch (IllegalArgumentException e) {
            log.warn("seo title apply refused for goods {}: {}", body.goodsId(), e.getMessage());
            return ResponseUtil.fail(ERRNO_BAD_TITLE, e.getMessage());
        }
    }

    public record TitleUpdate(Integer goodsId, String title) {
    }
}
