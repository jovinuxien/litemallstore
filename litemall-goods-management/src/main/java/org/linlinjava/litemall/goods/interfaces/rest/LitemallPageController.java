package org.linlinjava.litemall.goods.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.content.PageService;
import org.linlinjava.litemall.goods.domain.model.dto.goods.GoodsServiceResponseCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Customer DIY-page reads (anonymous — {@code /srv/page/**} is on
 * public-paths). Normative contract: {@code docs/spec-page-palette-v1.md} §4.
 * Errno 642 on {@code /home} is the SPA's signal to render the legacy
 * hardcoded home — nothing is active until an admin activates a page.
 */
@RestController
@RequestMapping("/srv/page")
public class LitemallPageController {

    private final PageService pageService;

    public LitemallPageController(PageService pageService) {
        this.pageService = pageService;
    }

    @GetMapping("/home")
    public Object home() {
        Map<String, Object> view = pageService.activeHome();
        if (view == null) {
            return ResponseUtil.fail(GoodsServiceResponseCode.PAGE_NOT_ACTIVE, "no active home page");
        }
        return ResponseUtil.ok(view);
    }

    /** Active pages only; drafts are never served customer-side. */
    @GetMapping("/{id:\\d+}")
    public Object byId(@PathVariable Integer id) {
        Map<String, Object> view = pageService.activeById(id);
        if (view == null) {
            return ResponseUtil.fail(GoodsServiceResponseCode.PAGE_NOT_ACTIVE, "page not active");
        }
        return ResponseUtil.ok(view);
    }
}
