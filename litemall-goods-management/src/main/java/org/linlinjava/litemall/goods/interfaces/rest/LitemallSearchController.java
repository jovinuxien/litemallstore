package org.linlinjava.litemall.goods.interfaces.rest;

import jakarta.validation.constraints.NotEmpty;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallSearchHistory;
import org.linlinjava.litemall.goods.application.search.CategorySearchService;
import org.linlinjava.litemall.goods.application.search.SearchHistoryService;
import org.linlinjava.litemall.goods.application.search.SearchKeywordService;
import org.linlinjava.litemall.goods.application.search.SearchService;
import org.linlinjava.litemall.goods.utils.UserContext;
import org.springframework.web.client.RestClientException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/srv/search")
public class LitemallSearchController {

    /**
     * Reserved params handled explicitly; everything else is treated as a candidate facet filter.
     * {@code offset}/{@code limit} are reserved too: some callers (the InstantSearch adapter) send
     * raw OCS pagination params alongside {@code page}/{@code size}; without stripping them here they
     * leak into the filter map and OcsSearchClient re-appends them, producing a duplicated/zeroed
     * {@code offset=&limit=} on the OCS URL. {@code highlight} joined the reserved set in Wave 9:
     * OcsSearchClient always sends it, so a caller-supplied copy must not leak in as a filter.
     */
    private static final Set<String> RESERVED_PARAMS = Set.of("q", "page", "size", "sort", "offset", "limit", "highlight");

    private final Log logger = LogFactory.getLog(LitemallSearchController.class);

    private final SearchService searchService;
    private final SearchKeywordService searchKeywordService;
    private final CategorySearchService categorySearchService;
    private final SearchHistoryService searchHistoryService;

    public LitemallSearchController(SearchService searchService,
                                    SearchKeywordService searchKeywordService,
                                    CategorySearchService categorySearchService,
                                    SearchHistoryService searchHistoryService) {
        this.searchService = searchService;
        this.searchKeywordService = searchKeywordService;
        this.categorySearchService = categorySearchService;
        this.searchHistoryService = searchHistoryService;
    }

    @GetMapping
    public Object search(@RequestParam(value = "q", required = false) String query,
                         @RequestParam(value = "page", defaultValue = "1") Integer page,
                         @RequestParam(value = "size", defaultValue = "20") Integer size,
                         @RequestParam(value = "sort", required = false) String sort,
                         @RequestParam Map<String, String> allParams) {
        Map<String, String> filters = new HashMap<>(allParams);
        filters.keySet().removeAll(RESERVED_PARAMS);
        // Record BEFORE the OCS round-trip (upstream litemall-wx-api ordering):
        // the keyword is the user's intent, and an OCS outage — which surfaces
        // as an exception from search() — must not skip the history write.
        LitemallSearchHistory historyRow = recordHistory(query);
        try {
            // SearchService whitelists these to the index's Facet fields before they reach OCS.
            Map<String, Object> result = searchService.search(query, page, size, sort, filters);
            // Wave 22: stamp the hit total back onto the history row (result_count; 0 = a real
            // zero-result search). Best-effort like the record — an OCS failure leaves it NULL.
            recordResultCount(historyRow, result);
            return ResponseUtil.ok(result);
        } catch (RestClientException e) {
            // OCS unreachable/erroring must degrade to a typed error, never a 5xx (Wave 9).
            logger.warn("OCS search unavailable for q='" + query + "': " + e.getMessage());
            return ResponseUtil.fail(502, "Search is temporarily unavailable");
        }
    }

    /** Best-effort history write for logged-in searches — must NEVER fail the search itself. */
    private LitemallSearchHistory recordHistory(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        Integer userId = resolveUserId();
        if (userId == null) {
            return null;
        }
        try {
            return searchHistoryService.record(userId, query);
        } catch (Exception e) {
            logger.warn("search-history write failed for user " + userId, e);
            return null;
        }
    }

    /** Best-effort result_count stamp (Wave 22) — null-safe, must NEVER fail the search itself. */
    private void recordResultCount(LitemallSearchHistory historyRow, Map<String, Object> result) {
        if (historyRow == null || result == null || !(result.get("total") instanceof Number total)) {
            return;
        }
        try {
            searchHistoryService.recordResultCount(historyRow, total.longValue());
        } catch (Exception e) {
            logger.warn("search-history result_count write failed for row " + historyRow.getId(), e);
        }
    }

    /** Clears the caller's search history (litemall-wx-api {@code /wx/search/clearhistory} parity). */
    @PostMapping("/clearhistory")
    public Object clearHistory() {
        Integer userId = resolveUserId();
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        searchHistoryService.clear(userId);
        return ResponseUtil.ok();
    }

    /**
     * Category-scoped faceted search: the landing page reached by clicking a category. Returns the
     * category's goods plus a left-rail that conforms to that category — breadcrumb, a subcategory
     * tree with per-node counts, and the brand/price/variant/attribute facets OCS scopes to it.
     * Accepts the same drill-down filter params as {@link #search} ({@code brand}, {@code price},
     * {@code source}, attribute names); {@code category_ids} is taken from the path, not the query.
     */
    @GetMapping("/category/{id}")
    public Object searchByCategory(@PathVariable Integer id,
                                   @RequestParam(value = "q", required = false) String query,
                                   @RequestParam(value = "page", defaultValue = "1") Integer page,
                                   @RequestParam(value = "size", defaultValue = "20") Integer size,
                                   @RequestParam(value = "sort", required = false) String sort,
                                   @RequestParam Map<String, String> allParams) {
        if (id == null || id <= 0) {
            return ResponseUtil.fail(404, "Category not found");
        }
        Map<String, String> filters = new HashMap<>(allParams);
        filters.keySet().removeAll(RESERVED_PARAMS);
        // The category scope is authoritative from the path; never let a query param override it.
        filters.remove("category_ids");
        Map<String, Object> result;
        try {
            result = categorySearchService.searchByCategory(id, query, page, size, sort, filters);
        } catch (RestClientException e) {
            logger.warn("OCS search unavailable for category " + id + ": " + e.getMessage());
            return ResponseUtil.fail(502, "Search is temporarily unavailable");
        }
        if (result == null) {
            return ResponseUtil.fail(404, "Category not found");
        }
        return ResponseUtil.ok(result);
    }

    /**
     * Typed autocomplete (Wave-9 contract): entries are {@code {text, type, categoryId?}} with
     * {@code type} keyword/category/curated — one source for the SPA's dropdown, merging OCS
     * suggestions with the curated keyword table. Plain-string entries remain legal per the
     * contract; consumers must accept both shapes. Fail-soft inside the service: OCS down ⇒
     * curated-only, never an error.
     */
    @GetMapping("/suggest")
    public Object suggest(@RequestParam("q") String query) {
        return ResponseUtil.ok(searchService.suggest(query));
    }

    /**
     * Search-box chrome (litemall-wx-api {@code /wx/search/index} parity): default + hot keywords,
     * plus the caller's own search history when an X-User-Id was forwarded (empty for anonymous).
     */
    @GetMapping("/index")
    public Object index() {
        return ResponseUtil.ok(searchKeywordService.index(resolveUserId()));
    }

    /** Keyword autocomplete from the curated keyword table (litemall-wx-api {@code /wx/search/helper}). */
    @GetMapping("/helper")
    public Object helper(@NotEmpty String keyword,
                         @RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "10") Integer limit) {
        return ResponseUtil.ok(searchKeywordService.helper(keyword, page, limit));
    }

    /** Optional current customer from the trusted X-User-Id header; null = anonymous visitor. */
    private static Integer resolveUserId() {
        String raw = UserContext.getUserId();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
