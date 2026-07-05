package org.linlinjava.litemall.goods.interfaces.rest.admin;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.service.LitemallGoodsProductService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.db.service.LitemallOrderService;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin dashboard totals, ported from litemall-admin-api
 * ({@code admin.web.AdminDashbordController} — typo fixed in the class name).
 * Mounted under {@code /srv/private/admin/**} (ROLE_ADMIN-gated by litemall-svcsecurity).
 */
@RestController
@RequestMapping("/srv/private/admin/dashboard")
@Validated
public class AdminDashboardController {
    private final Log logger = LogFactory.getLog(AdminDashboardController.class);

    @Autowired
    private LitemallUserService userService;
    @Autowired
    private LitemallGoodsService goodsService;
    @Autowired
    private LitemallGoodsProductService productService;
    @Autowired
    private LitemallOrderService orderService;

    @GetMapping("")
    public Object info() {
        Map<String, Integer> data = new HashMap<>();
        data.put("userTotal", userService.count());
        data.put("goodsTotal", goodsService.count());
        data.put("productTotal", productService.count());
        data.put("orderTotal", orderService.count());
        return ResponseUtil.ok(data);
    }
}
