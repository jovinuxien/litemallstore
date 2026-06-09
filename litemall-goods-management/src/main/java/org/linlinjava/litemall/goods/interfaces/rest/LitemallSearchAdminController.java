package org.linlinjava.litemall.goods.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.search.SearchReindexService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin-only search maintenance. Mapped under {@code /srv/private/admin/**} so
 * the ADMIN gate is enforced by both litemall-gateway-admin and the
 * litemall-svcsecurity resource-server filter chain (both require
 * {@code ROLE_ADMIN} on that path prefix). Path-based gating is the app's
 * convention; no method-level {@code @PreAuthorize} is wired.
 */
@RestController
@RequestMapping("/srv/private/admin/search")
public class LitemallSearchAdminController {

    private final SearchReindexService reindexService;

    public LitemallSearchAdminController(SearchReindexService reindexService) {
        this.reindexService = reindexService;
    }

    @PostMapping("/reindex")
    public Object reindex() {
        int count = reindexService.reindexAll();
        return ResponseUtil.ok(Map.of("indexed", count));
    }
}
