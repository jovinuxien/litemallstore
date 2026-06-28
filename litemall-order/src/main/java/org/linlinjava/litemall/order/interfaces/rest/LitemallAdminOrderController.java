package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin order surface for the admin SPA (litemall-gateway-admin). Served under
 * {@code /srv/private/admin/order/**} — the same admin namespace goods-management
 * uses for brand/category/etc. The edge gateway gates this prefix to ROLE_ADMIN
 * and relays the validated identity (machine token); this controller adds no
 * auth of its own. Read-only: a paged "all orders" list and a per-order detail.
 *
 * Built on the order module's own DDD repositories (LitemallOrderMapper-backed),
 * not the legacy litemall-db LitemallOrderService.queryVoSelective — that path's
 * OrderMapper.getOrderList has no SQL binding in this codebase (would 502).
 * Aggregates are mapped to flat DTOs so prices land as numbers (not LitemallMoney
 * objects) and dates as strings — the shapes the SPA already reads.
 */
@RestController
@RequestMapping("/srv/private/admin/order")
public class LitemallAdminOrderController {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Map the SPA's sort keys to vetted order_status table columns (the value is
    // interpolated into ORDER BY, so it must never come straight from the request).
    private static final Map<String, String> SORT_COLUMNS = new HashMap<>();
    static {
        SORT_COLUMNS.put("add_time", "add_time");
        SORT_COLUMNS.put("id", "id");
        SORT_COLUMNS.put("order_sn", "order_sn");
        SORT_COLUMNS.put("order_status", "order_status");
        SORT_COLUMNS.put("actual_price", "actual_price");
    }

    private final LitemallOrderRepository orderRepository;
    private final LitemallOrderGoodsRepository orderGoodsRepository;

    @Autowired
    public LitemallAdminOrderController(LitemallOrderRepository orderRepository,
                                        LitemallOrderGoodsRepository orderGoodsRepository) {
        this.orderRepository = orderRepository;
        this.orderGoodsRepository = orderGoodsRepository;
    }

    /** Paged list of all orders. Returns the okList envelope { list, total, page, limit, pages }. */
    @GetMapping("/list")
    public Object list(String orderSn,
                       @RequestParam(required = false) List<Short> orderStatusArray,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit,
                       @RequestParam(defaultValue = "add_time") String sort,
                       @RequestParam(defaultValue = "desc") String order) {
        String sortColumn = SORT_COLUMNS.getOrDefault(sort, "add_time");
        List<LitemallOrderAggregate> orders = orderRepository.adminQuery(orderSn, orderStatusArray, page, limit, sortColumn, order);
        long total = orderRepository.adminCount(orderSn, orderStatusArray);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (LitemallOrderAggregate o : orders) {
            rows.add(toRow(o));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("list", rows);
        data.put("total", total);
        data.put("page", page);
        data.put("limit", limit);
        data.put("pages", limit == 0 ? 0 : (int) Math.ceil((double) total / limit));
        return ResponseUtil.ok(data);
    }

    /** Per-order detail: { order, orderGoods, user }. */
    @GetMapping("/detail")
    public Object detail(@RequestParam Integer id) {
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        LitemallOrderAggregate o = orderRepository.findById(new LitemallOrderId(id)).orElse(null);
        if (o == null) {
            return ResponseUtil.badArgumentValue();
        }

        Map<String, Object> order = toRow(o);
        order.put("address", o.getAddress());
        order.put("message", o.getMessage());
        order.put("shipChannel", o.getShipChannel());
        order.put("shipSn", o.getShipSn());
        order.put("orderPrice", money(o.getOrderPrice()));
        order.put("freightPrice", money(o.getFreightPrice()));
        order.put("payTime", date(o.getPayTime()));

        List<Map<String, Object>> goods = new ArrayList<>();
        for (LitemallOrderGoodsAggregate g : orderGoodsRepository.findByOId(new LitemallOrderId(id))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", g.getOrderGoodsId());
            row.put("goodsName", g.getGoodsName());
            row.put("goodsSn", g.getGoodsSn());
            row.put("picUrl", g.getPicUrl());
            row.put("specifications", g.getSpecifications());
            row.put("price", money(g.getPrice()));
            row.put("number", g.getNumber());
            goods.add(row);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("order", order);
        data.put("orderGoods", goods);
        data.put("user", null); // user lookup not wired here; SPA falls back to the order's userId
        return ResponseUtil.ok(data);
    }

    private Map<String, Object> toRow(LitemallOrderAggregate o) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", o.getOrderId() == null ? null : o.getOrderId().getId());
        row.put("orderSn", o.getOrderSn());
        row.put("orderStatus", o.getOrderStatus() == null ? null : (int) o.getOrderStatus().getCode());
        row.put("actualPrice", money(o.getActualPrice()));
        row.put("addTime", date(o.getAddTime()));
        row.put("userId", o.getUserId() == null ? null : o.getUserId().getId());
        row.put("consignee", o.getConsignee());
        row.put("mobile", o.getMobile());
        return row;
    }

    private static BigDecimal money(LitemallMoney m) {
        return m == null ? BigDecimal.ZERO : m.getAmount();
    }

    private static String date(LocalDateTime t) {
        return t == null ? null : t.format(DT);
    }
}
