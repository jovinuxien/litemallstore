package org.linlinjava.litemall.goods.interfaces.rest;


import org.linlinjava.litemall.goods.application.search.SearchReindexService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("srv/admin")
public class LitemallGoodsAdminController {

    private final SearchReindexService searchReindexService;

    public LitemallGoodsAdminController(SearchReindexService searchReindexService) {
        this.searchReindexService = searchReindexService;
    }

    @GetMapping("/goods/ping")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public Object getPing(Principal principal) {
        Map<String, Object> data = new HashMap<>();
        data.put("hello", "world");
        data.put("principal username", principal.getName());
        return data;
    }

    /**
     * Full reindex of the on-sale catalog into OCS. Delegates to the same
     * {@link SearchReindexService} as {@code POST /srv/private/admin/search/reindex}
     * (session-based full replace, verified against ocs-indexer-service) so a single
     * reindex implementation exists; this legacy route is kept for compatibility.
     */
    @PostMapping("/goods/reindex")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public Map<String, Object> fullReindex() {
        return Map.of("indexed", searchReindexService.reindexAll());
    }
}
