package org.linlinjava.litemall.goods.interfaces.rest;

import jakarta.validation.constraints.NotEmpty;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.search.CategorySearchService;
import org.linlinjava.litemall.goods.application.search.SearchHistoryService;
import org.linlinjava.litemall.goods.application.search.SearchKeywordService;
import org.linlinjava.litemall.goods.application.search.SearchService;
import org.linlinjava.litemall.goods.utils.UserContext;
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
     * {@code offset=&limit=} on the OCS URL.
     */
    private static final Set<String> RESERVED_PARAMS = Set.of("q", "page", "size", "sort", "offset", "limit");

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
        // SearchService whitelists these to the index's Facet fields before they reach OCS.
        Object result = ResponseUtil.ok(searchService.search(query, page, size, sort, filters));
        recordHistory(query);
        return result;
    }

    /** Best-effort history write for logged-in searches — must NEVER fail the search itself. */
    private void recordHistory(String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        Integer userId = resolveUserId();
        if (userId == null) {
            return;
        }
        try {
            searchHistoryService.record(userId, query);
        } catch (Exception e) {
            logger.warn("search-history write failed for user " + userId, e);
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
        Map<String, Object> result = categorySearchService.searchByCategory(id, query, page, size, sort, filters);
        if (result == null) {
            return ResponseUtil.fail(404, "Category not found");
        }
        return ResponseUtil.ok(result);
    }

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
