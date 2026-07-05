package org.linlinjava.litemall.goods.interfaces.rest.admin;

import jakarta.validation.constraints.NotNull;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.goods.application.goods.admin.AdminGoodsService;
import org.linlinjava.litemall.goods.interfaces.rest.admin.dto.GoodsAllinone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Bulk create (SPA import / bulk-add). Each element goes through the same
     * transactional {@link AdminGoodsService#create} as the single endpoint —
     * one transaction per row, so a bad row fails alone and every created row
     * still fires its post-commit OCS index event. Returns
     * {@code {created, failed: [{index, name, error}]}}.
     */
    @PostMapping("/batch-create")
    public Object batchCreate(@RequestBody List<GoodsAllinone> goodsList) {
        if (goodsList == null || goodsList.isEmpty()) {
            return ResponseUtil.badArgument();
        }
        if (goodsList.size() > 500) {
            return ResponseUtil.fail(400, "batch too large: " + goodsList.size() + " rows (max 500)");
        }
        int created = 0;
        List<Map<String, Object>> failed = new ArrayList<>();
        for (int i = 0; i < goodsList.size(); i++) {
            GoodsAllinone one = goodsList.get(i);
            String name = one != null && one.getGoods() != null ? one.getGoods().getName() : null;
            try {
                Object result = adminGoodsService.create(one);
                Object errno = result instanceof Map ? ((Map<?, ?>) result).get("errno") : null;
                if (Integer.valueOf(0).equals(errno)) {
                    created++;
                } else {
                    Object errmsg = result instanceof Map ? ((Map<?, ?>) result).get("errmsg") : null;
                    failed.add(rowError(i, name, errmsg == null ? "create failed" : String.valueOf(errmsg)));
                }
            } catch (Exception e) {
                logger.error("batch-create row " + i + " (" + name + ") failed", e);
                failed.add(rowError(i, name, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("created", created);
        data.put("failed", failed);
        return ResponseUtil.ok(data);
    }

    private static Map<String, Object> rowError(int index, String name, String error) {
        Map<String, Object> row = new HashMap<>();
        row.put("index", index);
        row.put("name", name);
        row.put("error", error);
        return row;
    }

    @GetMapping("/detail")
    public Object detail(@NotNull Integer id) {
        return adminGoodsService.detail(id);
    }

}
