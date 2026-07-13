package org.linlinjava.litemall.goods.interfaces.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.region.RegionQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Region cascade for address forms (litemall-wx-api {@code WxRegionController} parity).
 * Anonymous reads; served from {@link RegionQueryService}'s in-memory snapshot, so only the very
 * first call after boot touches the database.
 */
@RestController
@RequestMapping("/srv/region")
public class LitemallRegionController {
    private final Log logger = LogFactory.getLog(LitemallRegionController.class);

    private final RegionQueryService regionQueryService;

    public LitemallRegionController(RegionQueryService regionQueryService) {
        this.regionQueryService = regionQueryService;
    }

    /** Children of {@code pid} ({@code pid=0} → the 31 provinces) as {id, pid, name, type, code}. */
    @GetMapping("/list")
    public Object list(@RequestParam(defaultValue = "0") Integer pid) {
        try {
            return ResponseUtil.ok(regionQueryService.listChildren(pid));
        } catch (Exception e) {
            logger.error("region snapshot build failed", e);
            return ResponseUtil.fail(502, "region data unavailable");
        }
    }

    /** Full province → city → county tree: [{id, name, code, children:[{..., children:[...]}]}]. */
    @GetMapping("/clist")
    public Object clist() {
        try {
            return ResponseUtil.ok(regionQueryService.tree());
        } catch (Exception e) {
            logger.error("region snapshot build failed", e);
            return ResponseUtil.fail(502, "region data unavailable");
        }
    }
}
