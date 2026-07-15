package org.linlinjava.litemall.goods.interfaces.rest.admin;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.recommend.RelatedGoodsRefreshTask;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin recommendation surface ({@code /srv/private/admin/**} → machine token +
 * ROLE_ADMIN; rides gateway-admin's goods catch-all route). One endpoint: force
 * the nightly co-occurrence batch to run now — seeding a fresh environment or
 * verifying after demo orders, instead of waiting for the 02:45 cron.
 */
@RestController
@RequestMapping("/srv/private/admin/recommend")
public class AdminRecommendController {

    private final RelatedGoodsRefreshTask refreshTask;

    public AdminRecommendController(RelatedGoodsRefreshTask refreshTask) {
        this.refreshTask = refreshTask;
    }

    @PostMapping("/rebuild")
    public Object rebuild() {
        int rows = refreshTask.rebuild();
        return ResponseUtil.ok(Map.of("rows", rows));
    }
}
