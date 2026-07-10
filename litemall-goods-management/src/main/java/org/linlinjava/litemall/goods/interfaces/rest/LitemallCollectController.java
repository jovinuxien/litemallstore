package org.linlinjava.litemall.goods.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.engagement.CollectService;
import org.linlinjava.litemall.goods.utils.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Customer favorites ({@code /srv/collect}, litemall-wx-api {@code /wx/collect}
 * parity). Authenticated: NOT on the svcsecurity public-paths list, and the
 * owner is ALWAYS the gateway-injected {@code X-User-Id} — never a request
 * param (cart-IDOR rule). {@code valueId} accepts a numeric goods id or the
 * {@code cj_&lt;pid&gt;} doc reference.
 */
@RestController
@RequestMapping("/srv/collect")
public class LitemallCollectController {

    private final CollectService collectService;

    public LitemallCollectController(CollectService collectService) {
        this.collectService = collectService;
    }

    @GetMapping("/list")
    public Object list(@RequestParam(defaultValue = "0") Byte type,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit) {
        Integer userId = UserContext.getUserIdAsInt();
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        return ResponseUtil.ok(collectService.list(userId, type, page, limit));
    }

    @PostMapping("/addordelete")
    public Object addOrDelete(@RequestBody Map<String, Object> body) {
        Integer userId = UserContext.getUserIdAsInt();
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Byte type = body.get("type") != null ? Byte.valueOf(String.valueOf(body.get("type"))) : 0;
        String valueId = body.get("valueId") != null ? String.valueOf(body.get("valueId")) : null;
        Map<String, Object> result = collectService.addOrDelete(userId, type, valueId);
        if (result == null) {
            return ResponseUtil.badArgumentValue();
        }
        return ResponseUtil.ok(result);
    }
}
