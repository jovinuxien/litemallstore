package org.linlinjava.litemall.goods.interfaces.rest;

import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsSearchClient;
import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsSuggestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * SPA-facing search endpoints. Both delegate to the OCS ACL clients —
 * goods-management owns mapping the OCS response shape onto the goods-list
 * DTO the SPA already consumes (no SQL fallback for the search path).
 */
@RestController
@RequestMapping("/srv")
public class LitemallSearchController {

    private final OcsSearchClient searchClient;
    private final OcsSuggestClient suggestClient;

    public LitemallSearchController(OcsSearchClient searchClient, OcsSuggestClient suggestClient) {
        this.searchClient = searchClient;
        this.suggestClient = suggestClient;
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam("q") String query,
                                      @RequestParam(value = "offset", defaultValue = "0") int offset,
                                      @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return searchClient.search(query, offset, limit);
    }

    @GetMapping("/suggest")
    public List<String> suggest(@RequestParam("q") String prefix) {
        return suggestClient.suggest(prefix);
    }
}
