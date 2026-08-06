package org.linlinjava.litemall.promotion.interfaces.rest.legacy;

import org.linlinjava.litemall.promotion.application.LitemallPromotionOrchestratorService;
import org.linlinjava.litemall.promotion.application.internal.LitemallCouponServiceImpl;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallUserCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallExchangeCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallReceiveCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallUserCouponStatus;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Legacy-path coupon surface matching the customer SPA's existing
 * {@code userApi.ts} contract ({@code /srv/coupon/*}, {@code errno/errmsg/data}
 * envelope, {@code ICoupon} field names). The canonical DDD surface lives at
 * {@code /srv/promotion/coupon/**}; this controller only adapts shapes so the
 * gateway can route the SPA's calls here unchanged (see
 * docs/spec-gateway-routes.md).
 */
@RestController
@RequestMapping("/srv/coupon")
public class LitemallCouponLegacyController {

    private final LitemallPromotionOrchestratorService orchestratorService;

    public LitemallCouponLegacyController(LitemallPromotionOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    /** Claimable coupons (legacy litemall {@code /wx/coupon/list}). */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        List<LitemallCouponAggregate> receivable =
                orchestratorService.getCouponService().getReceivableCoupons();
        int from = Math.min((page - 1) * limit, receivable.size());
        int to = Math.min(from + limit, receivable.size());
        List<Map<String, Object>> list = receivable.subList(from, to).stream()
                .map(this::toLegacyCoupon)
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", receivable.size());
        data.put("page", page);
        data.put("limit", limit);
        return ApiResponse.ok(data);
    }

    /**
     * My coupons by status tab: 0 unused / 1 used / 2 expired — the SPA's
     * {@code Coupons.tsx} tab index is the status code.
     */
    @GetMapping("/mylist")
    public ApiResponse<Map<String, Object>> myList(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        LitemallUserCouponStatus statusFilter =
                status != null ? LitemallUserCouponStatus.fromCode(status) : null;
        List<LitemallUserCouponAggregate> held = orchestratorService.getCouponService()
                .getMyCoupons(new LitemallUserId(userId), statusFilter);
        int from = Math.min((page - 1) * limit, held.size());
        int to = Math.min(from + limit, held.size());
        List<Map<String, Object>> list = held.subList(from, to).stream()
                .map(this::toLegacyHeldCoupon)
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", held.size());
        data.put("page", page);
        data.put("limit", limit);
        return ApiResponse.ok(data);
    }

    /**
     * Coupons usable for the current checkout. Unlike legacy litemall's
     * {@code cartId} parameter, the caller passes the cart facts directly
     * (amount + goods/category ids) — promotion has no cart access.
     */
    @GetMapping("/selectlist")
    public ApiResponse<List<Map<String, Object>>> selectList(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) List<Integer> goodsIds,
            @RequestParam(required = false) List<Integer> categoryIds) {
        List<Map<String, Object>> list = orchestratorService.getCouponService()
                .getUsableForCheckout(new LitemallUserId(userId), amount, goodsIds, categoryIds).stream()
                .map(view -> {
                    Map<String, Object> item = toLegacyCoupon(view.getCoupon());
                    item.put("id", view.getUserCoupon().getUserCouponId().getId());
                    item.put("cid", view.getCoupon().getCouponId().getId());
                    item.put("startTime", view.getUserCoupon().getStartTime());
                    item.put("endTime", view.getUserCoupon().getEndTime());
                    // Wave 18: the checkout picker gets the COMPUTED effective
                    // discount for this cart amount (percent rate + cap
                    // resolved server-side) so order-side math is unchanged;
                    // the raw rate stays available as discountRate.
                    if (view.getCoupon().isPercent()) {
                        item.put("discountRate", view.getCoupon().getDiscount() != null
                                ? view.getCoupon().getDiscount().getAmount() : null);
                    }
                    item.put("discount", view.getEffectiveDiscount());
                    return item;
                })
                .collect(Collectors.toList());
        return ApiResponse.ok(list);
    }

    /** Claim a coupon (legacy body: {@code {couponId}}). */
    @PostMapping("/receive")
    public ApiResponse<Map<String, Object>> receive(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestBody Map<String, Object> body) {
        Object couponId = body.get("couponId");
        if (!(couponId instanceof Number)) {
            return ApiResponse.fail(402, "couponId is required");
        }
        LitemallPromotionOperationResult result = orchestratorService.receiveCoupon(
                new LitemallReceiveCouponCommand(new LitemallUserId(userId),
                        new LitemallCouponId(((Number) couponId).intValue()), null));
        return result.isSuccess()
                ? ApiResponse.ok(result.getData())
                : ApiResponse.fail(502, result.getMessage());
    }

    /** Exchange a redemption code (legacy body: {@code {code}}). */
    @PostMapping("/exchange")
    public ApiResponse<Map<String, Object>> exchange(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestBody Map<String, Object> body) {
        Object code = body.get("code");
        if (!(code instanceof String) || ((String) code).isBlank()) {
            return ApiResponse.fail(402, "code is required");
        }
        LitemallPromotionOperationResult result = orchestratorService.exchangeCoupon(
                new LitemallExchangeCouponCommand(new LitemallUserId(userId), (String) code));
        return result.isSuccess()
                ? ApiResponse.ok(result.getData())
                : ApiResponse.fail(502, result.getMessage());
    }

    // ----- legacy shape mapping (ICoupon field names) -----

    private Map<String, Object> toLegacyCoupon(LitemallCouponAggregate c) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", c.getCouponId() != null ? c.getCouponId().getId() : null);
        item.put("name", c.getName());
        item.put("desc", c.getDescription());
        item.put("tag", c.getTag());
        item.put("discount", c.getDiscount() != null ? c.getDiscount().getAmount() : null);
        // Wave 18: 0 = flat ($D off), 1 = percent (discount holds the rate;
        // discountCap bounds the absolute amount, null = uncapped).
        item.put("discountType", c.getDiscountType() != null ? c.getDiscountType().getCode() : 0);
        item.put("discountCap", c.getDiscountCap() != null ? c.getDiscountCap().getAmount() : null);
        item.put("min", c.getMin() != null ? c.getMin().getAmount() : null);
        item.put("type", c.getType() != null ? c.getType().getCode() : null);
        item.put("status", c.getStatus() != null ? c.getStatus().getCode() : null);
        item.put("available", c.isAvailable());
        item.put("startTime", c.getStartTime());
        item.put("endTime", c.getEndTime());
        return item;
    }

    private Map<String, Object> toLegacyHeldCoupon(LitemallUserCouponAggregate held) {
        Optional<LitemallCouponAggregate> couponOpt = orchestratorService.getCouponService()
                .getCoupon(held.getCouponId());
        Map<String, Object> item = couponOpt.map(this::toLegacyCoupon)
                .orElseGet(LinkedHashMap::new);
        item.put("id", held.getUserCouponId() != null ? held.getUserCouponId().getId() : null);
        item.put("cid", held.getCouponId() != null ? held.getCouponId().getId() : null);
        item.put("status", held.getStatus() != null ? held.getStatus().getCode() : null);
        item.put("available", held.isUsable() && !held.isExpired(LocalDateTime.now()));
        item.put("startTime", held.getStartTime());
        item.put("endTime", held.getEndTime());
        return item;
    }
}
