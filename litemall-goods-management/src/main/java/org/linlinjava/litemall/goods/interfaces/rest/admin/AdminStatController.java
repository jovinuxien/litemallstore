package org.linlinjava.litemall.goods.interfaces.rest.admin;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.service.StatService;
import org.linlinjava.litemall.goods.interfaces.rest.admin.vo.StatVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Admin statistics, ported from litemall-admin-api ({@code admin.web.AdminStatController}).
 * Mounted under {@code /srv/private/admin/**} (ROLE_ADMIN-gated by litemall-svcsecurity).
 * Backed by litemall-db {@code StatService}/{@code StatMapper.xml} (per-day GROUP BY).
 */
@RestController
@RequestMapping("/srv/private/admin/stat")
@Validated
public class AdminStatController {
    private final Log logger = LogFactory.getLog(AdminStatController.class);

    @Autowired
    private StatService statService;

    @GetMapping("/user")
    public Object statUser() {
        List<Map> rows = statService.statUser();
        StatVo statVo = new StatVo();
        statVo.setColumns(new String[]{"day", "users"});
        statVo.setRows(rows);
        return ResponseUtil.ok(statVo);
    }

    @GetMapping("/order")
    public Object statOrder() {
        List<Map> rows = statService.statOrder();
        StatVo statVo = new StatVo();
        statVo.setColumns(new String[]{"day", "orders", "customers", "amount", "pcr"});
        statVo.setRows(rows);
        return ResponseUtil.ok(statVo);
    }

    @GetMapping("/goods")
    public Object statGoods() {
        List<Map> rows = statService.statGoods();
        StatVo statVo = new StatVo();
        statVo.setColumns(new String[]{"day", "orders", "products", "amount"});
        statVo.setRows(rows);
        return ResponseUtil.ok(statVo);
    }
}
