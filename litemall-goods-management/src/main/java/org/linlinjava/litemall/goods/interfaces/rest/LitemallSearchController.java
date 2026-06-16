package org.linlinjava.litemall.goods.interfaces.rest;

import jakarta.validation.constraints.NotEmpty;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.search.SearchKeywordService;
import org.linlinjava.litemall.goods.application.search.SearchService;
import org.linlinjava.litemall.goods.utils.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/srv/search")
public class LitemallSearchController {

    /** Reserved params handled explicitly; everything else is treated as a candidate facet filter. */
    private static final Set<String> RESERVED_PARAMS = Set.of("q", "page", "size", "sort");

    private final SearchService searchService;
    private final SearchKeywordService searchKeywordService;

    public LitemallSearchController(SearchService searchService,
                                    SearchKeywordService searchKeywordService) {
        this.searchService = searchService;
        this.searchKeywordService = searchKeywordService;
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
        return ResponseUtil.ok(searchService.search(query, page, size, sort, filters));
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
