package org.linlinjava.litemall.goods.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.engagement.FootprintService;
import org.linlinjava.litemall.goods.utils.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Customer browsing history ({@code /srv/footprint}, litemall-wx-api
 * {@code /wx/footprint} parity). Authenticated: NOT on the svcsecurity
 * public-paths list, and the owner is ALWAYS the gateway-injected
 * {@code X-User-Id} — never a request param (cart-IDOR rule). {@code goodsId}
 * accepts a numeric goods id or the {@code cj_&lt;pid&gt;} doc reference.
 */
@RestController
@RequestMapping("/srv/footprint")
public class LitemallFootprintController {

    private final FootprintService footprintService;

    public LitemallFootprintController(FootprintService footprintService) {
        this.footprintService = footprintService;
    }

    @GetMapping("/list")
    public Object list(@RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit) {
        Integer userId = UserContext.getUserIdAsInt();
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        return ResponseUtil.ok(footprintService.list(userId, page, limit));
    }

    /** Fire-and-forget from the product page; deduped per user+goods+day. */
    @PostMapping("/record")
    public Object record(@RequestBody Map<String, Object> body) {
        Integer userId = UserContext.getUserIdAsInt();
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        String goodsId = body.get("goodsId") != null ? String.valueOf(body.get("goodsId")) : null;
        footprintService.record(userId, goodsId);
        return ResponseUtil.ok();
    }

    @PostMapping("/delete")
    public Object delete(@RequestBody Map<String, Object> body) {
        Integer userId = UserContext.getUserIdAsInt();
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Integer id;
        try {
            id = body.get("id") != null ? Integer.valueOf(String.valueOf(body.get("id"))) : null;
        } catch (NumberFormatException e) {
            id = null;
        }
        if (id == null || !footprintService.delete(userId, id)) {
            return ResponseUtil.badArgumentValue();
        }
        return ResponseUtil.ok();
    }
}
