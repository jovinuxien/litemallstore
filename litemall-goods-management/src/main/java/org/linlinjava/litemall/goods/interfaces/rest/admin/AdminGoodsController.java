package org.linlinjava.litemall.goods.interfaces.rest.admin;

import jakarta.validation.constraints.NotNull;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.goods.application.goods.admin.AdminGoodsService;
import org.linlinjava.litemall.goods.interfaces.rest.admin.dto.GoodsAllinone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Admin goods management, ported from litemall-admin-api ({@code admin.web.AdminGoodsController}).
 *
 * <p>Mounted under {@code /srv/private/admin/**}, which litemall-svcsecurity gates on the
 * gateway-admin-forwarded {@code ROLE_ADMIN} (no per-method {@code @PreAuthorize} needed — same
 * convention as the reindex endpoint). The admin-api {@code @RequiresPermissionsDesc} (Shiro
 * menu-scan) annotations are dropped.
 */
@RestController
@RequestMapping("/srv/private/admin/goods")
@Validated
public class AdminGoodsController {
    private final Log logger = LogFactory.getLog(AdminGoodsController.class);

    @Autowired
    private AdminGoodsService adminGoodsService;

    @GetMapping("/list")
    public Object list(Integer goodsId, String goodsSn, String name,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit,
                       @Sort @RequestParam(defaultValue = "add_time") String sort,
                       @Order @RequestParam(defaultValue = "desc") String order) {
        return adminGoodsService.list(goodsId, goodsSn, name, page, limit, sort, order);
    }

    @GetMapping("/catAndBrand")
    public Object list2() {
        return adminGoodsService.list2();
    }

    @PostMapping("/update")
    public Object update(@RequestBody GoodsAllinone goodsAllinone) {
        return adminGoodsService.update(goodsAllinone);
    }

    @PostMapping("/delete")
    public Object delete(@RequestBody LitemallGoods goods) {
        return adminGoodsService.delete(goods);
    }

    @PostMapping("/create")
    public Object create(@RequestBody GoodsAllinone goodsAllinone) {
        return adminGoodsService.create(goodsAllinone);
    }

    @GetMapping("/detail")
    public Object detail(@NotNull Integer id) {
        return adminGoodsService.detail(id);
    }

}
