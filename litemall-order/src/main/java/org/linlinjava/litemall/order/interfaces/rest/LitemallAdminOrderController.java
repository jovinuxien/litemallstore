package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.linlinjava.litemall.order.interfaces.dtos.order.OrderOperationDtoResponse;
import org.linlinjava.litemall.order.interfaces.dtos.order.ShipActionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.linlinjava.litemall.order.interfaces.util.LitemallHttpResponseUtil.buildResponse;

/**
 * Admin order surface, served under {@code /srv/private/admin/order/**} — the same admin
 * namespace goods-management uses for brand/category/etc. The edge gateway gates this
 * prefix to ROLE_ADMIN and relays the validated identity (machine token); this controller
 * adds no auth of its own.
 *
 * <p>Two responsibilities:
 * <ul>
 *   <li><b>Read</b> — a paged "all orders" list and a per-order detail for the admin SPA
 *       (litemall-gateway-admin). Built on the order module's own DDD repositories
 *       (LitemallOrderMapper-backed), not the legacy LitemallOrderService.queryVoSelective
 *       (whose OrderMapper.getOrderList has no SQL binding here and would 502). Aggregates
 *       are mapped to flat DTOs so prices land as numbers and dates as strings.</li>
 *   <li><b>Lifecycle transitions</b> — admin-driven ship (PAID→SHIPPED) and refund approval
 *       (REFUND_REQUEST→REFUNDED, crediting the buyer's wallet back), through the
 *       orchestrator.</li>
 * </ul>
 *
 * <p>Routing note (gateway-admin follow-up): the admin gateway must route
 * {@code /srv/private/admin/order/**} to the order service for these to be reachable.
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
    private final LitemallOrderOrchestratorService orchestrator;
    // ACL over CJ (Wave 3): account-balance readout for the admin dashboard/order list.
    private final org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjDropshipOrderFacade cjOrderFacade;
    // Shipment tracking read (Wave 3): live CJ trackInfo for CJ orders, clean NOT_SHIPPED otherwise.
    private final org.linlinjava.litemall.order.application.internal.cj.CjTrackingService cjTrackingService;
    // Pickup stores (Wave 4): store name on the write-off counter payload.
    private final org.linlinjava.litemall.order.application.internal.LitemallStoreServiceLayer storeServiceLayer;

    @Autowired
    public LitemallAdminOrderController(LitemallOrderRepository orderRepository,
                                        LitemallOrderGoodsRepository orderGoodsRepository,
                                        LitemallOrderOrchestratorService orchestrator,
                                        org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjDropshipOrderFacade cjOrderFacade,
                                        org.linlinjava.litemall.order.application.internal.cj.CjTrackingService cjTrackingService,
                                        org.linlinjava.litemall.order.application.internal.LitemallStoreServiceLayer storeServiceLayer) {
        this.orderRepository = orderRepository;
        this.orderGoodsRepository = orderGoodsRepository;
        this.orchestrator = orchestrator;
        this.cjOrderFacade = cjOrderFacade;
        this.cjTrackingService = cjTrackingService;
        this.storeServiceLayer = storeServiceLayer;
    }

    // ---- read surface (admin SPA) ---------------------------------------------------

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
        // CJ linkage (V27/V33) for the admin CJ logistics/tracking panel.
        order.put("source", o.getSource());
        order.put("cjOrderId", o.getCjOrderId());
        order.put("cjOrderNum", o.getCjOrderNum());
        order.put("cjOrderStatus", o.getCjOrderStatus());

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

    // ---- lifecycle transitions (admin-driven) ---------------------------------------

    /** Ship a paid order (PAID → SHIPPED), recording courier + tracking number. */
    @PostMapping("/{orderId}/ship")
    public ResponseEntity<OrderOperationDtoResponse> ship(
            @PathVariable Integer orderId,
            @RequestBody ShipActionRequest request) {
        LitemallOrderOperationResult result = orchestrator.shipOrder(
                new LitemallOrderId(orderId), request.getShipChannel(), request.getShipSn());
        return buildResponse(result);
    }

    /**
     * Approve a pending refund (REFUND_REQUEST → REFUNDED): credits the buyer's wallet
     * back and flips the status atomically.
     */
    @PostMapping("/{orderId}/refund")
    public ResponseEntity<OrderOperationDtoResponse> approveRefund(@PathVariable Integer orderId) {
        LitemallOrderOperationResult result = orchestrator.approveRefund(new LitemallOrderId(orderId));
        return buildResponse(result);
    }

    // ---- pickup write-off (核销, Wave 4) ----------------------------------------------

    /**
     * Preview a scanned pickup verify code WITHOUT redeeming it: shows the counter
     * staff the order + line items so they hand over the right parcel. Literal path
     * segment (never collides with the {@code /{orderId}/...} mappings). Three distinct
     * 422s: unknown code / already verified / wrong state.
     */
    @GetMapping("/writeoff")
    public Object writeoffPreview(@RequestParam String verifyCode) {
        try {
            LitemallOrderAggregate order = orchestrator.writeoffPreview(verifyCode);
            return ResponseUtil.ok(writeoffPayload(order));
        } catch (org.linlinjava.litemall.order.application.util.exception.order.LitemallWriteoffException e) {
            return writeoffError(e);
        }
    }

    /**
     * Redeem a verify code: PAID → DELIVERED with the {@code writeoff} timeline hop and
     * a {@code verified_by = "admin:<X-User-Id>"} audit stamp. A double scan loses
     * cleanly (ALREADY_VERIFIED, no state change).
     */
    @PostMapping("/writeoff")
    public Object writeoffCommit(@RequestHeader(value = "X-User-Id", required = false) String adminUserId,
                                 @RequestBody Map<String, String> body) {
        String verifyCode = body.get("verifyCode");
        if (verifyCode == null || verifyCode.isBlank()) {
            return ResponseEntity.unprocessableEntity()
                    .body(ResponseUtil.fail(422, "verifyCode is required"));
        }
        String verifiedBy = "admin:" + (adminUserId == null || adminUserId.isBlank() ? "unknown" : adminUserId);
        try {
            LitemallOrderAggregate order = orchestrator.writeoffCommit(verifyCode, verifiedBy);
            return ResponseUtil.ok(writeoffPayload(order));
        } catch (org.linlinjava.litemall.order.application.util.exception.order.LitemallWriteoffException e) {
            return writeoffError(e);
        }
    }

    private Object writeoffError(
            org.linlinjava.litemall.order.application.util.exception.order.LitemallWriteoffException e) {
        // errno 422 + kind: the SPA can branch on kind while the errmsg names the exact
        // problem (three distinct client errors, per the Task-B acceptance).
        return ResponseEntity.unprocessableEntity()
                .body(ResponseUtil.fail(422, "[" + e.getKind() + "] " + e.getMessage()));
    }

    /** Counter payload: order header + store + a lean line-item summary. */
    private Map<String, Object> writeoffPayload(LitemallOrderAggregate order) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", order.getOrderId().getId());
        data.put("orderSn", order.getOrderSn());
        data.put("orderStatus", order.getOrderStatus().getCode());
        data.put("orderStatusText", order.getOrderStatus().getDisplayName());
        data.put("deliveryType", order.getDeliveryType());
        data.put("consignee", order.getConsignee());
        data.put("mobile", order.getMobile());
        data.put("actualPrice", order.getActualPrice() == null ? null : order.getActualPrice().getAmount());
        data.put("storeId", order.getStoreId());
        org.linlinjava.litemall.db.domain.LitemallStore store = storeServiceLayer.findById(order.getStoreId());
        data.put("storeName", store == null ? null : store.getName());
        data.put("verifyTime", order.getVerifyTime());
        data.put("verifiedBy", order.getVerifiedBy());
        List<Map<String, Object>> items = new ArrayList<>();
        for (LitemallOrderGoodsAggregate g : orderGoodsRepository.findByOId(order.getOrderId())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("goodsName", g.getGoodsName());
            item.put("number", g.getNumber());
            item.put("specifications", g.getSpecifications());
            items.add(item);
        }
        data.put("items", items);
        return data;
    }

    // ---- CJ (Wave 3) ----------------------------------------------------------------

    /**
     * Shipment tracking for an order (Wave 3): carrier + tracking number + event list, live
     * from CJ for {@code source='cj'} orders. Not shipped yet → clean {@code NOT_SHIPPED}
     * payload. Contract: docs/handoff-gateway-admin-cj-tracking.md.
     */
    @GetMapping("/{orderId}/tracking")
    public Object tracking(@PathVariable Integer orderId) {
        org.linlinjava.litemall.order.interfaces.dtos.cj.tracking.TrackingDtoResponse dto =
                cjTrackingService.getTrackingForAdmin(new LitemallOrderId(orderId));
        return dto == null ? ResponseUtil.badArgumentValue() : ResponseUtil.ok(dto);
    }

    /**
     * CJ account balance ({@code shopping/pay/getBalance}) for the admin readout.
     * Literal path segment, so it never collides with the {@code /{orderId}/...} mappings.
     * CJ unreachable → clean errno payload, never a raw 500.
     */
    @GetMapping("/cj/balance")
    public Object cjBalance() {
        return cjOrderFacade.getBalance()
                .<Object>map(b -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("amount", b.getAmount());
                    data.put("noWithdrawalAmount", b.getNoWithdrawalAmount());
                    data.put("freezeAmount", b.getFreezeAmount());
                    return ResponseUtil.ok(data);
                })
                .orElseGet(() -> ResponseUtil.fail(502, "CJ balance unavailable"));
    }

    // ---- mapping helpers ------------------------------------------------------------

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
        // Wave 4: delivery mode + fulfillment source so the admin list can badge
        // pickup orders (write-off flow) and dropship orders.
        row.put("deliveryType", o.getDeliveryType());
        row.put("source", o.getSource());
        return row;
    }

    private static BigDecimal money(LitemallMoney m) {
        return m == null ? BigDecimal.ZERO : m.getAmount();
    }

    private static String date(LocalDateTime t) {
        return t == null ? null : t.format(DT);
    }
}
