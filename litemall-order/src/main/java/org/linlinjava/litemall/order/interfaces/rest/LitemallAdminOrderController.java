package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.db.domain.LitemallOrderGoods;
import org.linlinjava.litemall.db.domain.UserVo;
import org.linlinjava.litemall.db.service.LitemallOrderGoodsService;
import org.linlinjava.litemall.db.service.LitemallOrderService;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin order surface for the admin SPA (litemall-gateway-admin). Served under
 * {@code /srv/private/admin/order/**} — the same admin namespace goods-management
 * uses for brand/category/etc. The edge gateway gates this prefix to ROLE_ADMIN
 * and relays the validated identity (machine token); this controller adds no
 * auth of its own. Read-only: a paged "all orders" list and a per-order detail,
 * sourced from the shared litemall-db services (same data the customer order
 * aggregates persist to).
 */
@RestController
@RequestMapping("/srv/private/admin/order")
public class LitemallAdminOrderController {

    // queryVoSelective interpolates sort/order into the ORDER BY clause, so the
    // inputs are whitelisted here rather than trusted from the request.
    private static final List<String> SORTABLE = Arrays.asList("add_time", "id", "order_sn", "order_status", "actual_price");

    private final LitemallOrderService orderService;
    private final LitemallOrderGoodsService orderGoodsService;
    private final LitemallUserService userService;

    @Autowired
    public LitemallAdminOrderController(LitemallOrderService orderService,
                                        LitemallOrderGoodsService orderGoodsService,
                                        LitemallUserService userService) {
        this.orderService = orderService;
        this.orderGoodsService = orderGoodsService;
        this.userService = userService;
    }

    /** Paged list of all orders. Returns the okList envelope { list, total, page, limit, pages }. */
    @GetMapping("/list")
    public Object list(String nickname, String consignee, String orderSn,
                       @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
                       @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
                       @RequestParam(required = false) List<Short> orderStatusArray,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit,
                       @RequestParam(defaultValue = "add_time") String sort,
                       @RequestParam(defaultValue = "desc") String order) {
        String safeSort = SORTABLE.contains(sort) ? sort : "add_time";
        String safeOrder = "asc".equalsIgnoreCase(order) ? "asc" : "desc";
        Map<String, Object> data = orderService.queryVoSelective(nickname, consignee, orderSn, start, end,
                orderStatusArray, page, limit, safeSort, safeOrder);
        return ResponseUtil.ok(data);
    }

    /** Per-order detail: { order, orderGoods, user }. */
    @GetMapping("/detail")
    public Object detail(@RequestParam Integer id) {
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        LitemallOrder order = orderService.findById(id);
        if (order == null) {
            return ResponseUtil.badArgumentValue();
        }
        List<LitemallOrderGoods> orderGoods = orderGoodsService.queryByOid(id);
        UserVo user = userService.findUserVoById(order.getUserId());

        Map<String, Object> data = new HashMap<>();
        data.put("order", order);
        data.put("orderGoods", orderGoods);
        data.put("user", user);
        return ResponseUtil.ok(data);
    }
}
