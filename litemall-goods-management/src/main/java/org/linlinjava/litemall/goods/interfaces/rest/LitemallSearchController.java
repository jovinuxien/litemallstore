package org.linlinjava.litemall.goods.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.search.SearchService;
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

    public LitemallSearchController(SearchService searchService) {
        this.searchService = searchService;
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
}
