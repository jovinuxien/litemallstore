package org.linlinjava.litemall.promotion.interfaces.rest;

import org.linlinjava.litemall.promotion.application.LitemallPromotionOrchestratorService;
import org.linlinjava.litemall.promotion.application.internal.LitemallCouponServiceImpl;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallUserCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallExchangeCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallReceiveCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallRedeemCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallReleaseCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallUserCouponStatus;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.linlinjava.litemall.promotion.interfaces.dtos.CouponDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.CouponExchangeRequest;
import org.linlinjava.litemall.promotion.interfaces.dtos.CouponRedeemRequest;
import org.linlinjava.litemall.promotion.interfaces.dtos.CouponReleaseRequest;
import org.linlinjava.litemall.promotion.interfaces.dtos.PromotionOperationDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.UserCouponDtoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static org.linlinjava.litemall.promotion.interfaces.util.LitemallHttpResponseUtil.buildResponse;

/**
 * Customer-facing coupon endpoints (crmeb H5 surface). Authenticated as the
 * customer; the user id is taken from the forwarded request context. Gateway
 * routing of {@code /srv/promotion/**} is a gateway-worktree follow-up.
 */
@RestController
@RequestMapping("/srv/promotion/coupon")
public class LitemallCouponController {

    private final LitemallPromotionOrchestratorService orchestratorService;

    public LitemallCouponController(LitemallPromotionOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @GetMapping("/available")
    public ResponseEntity<List<CouponDtoResponse>> getReceivableCoupons() {
        List<CouponDtoResponse> response = orchestratorService.getCouponService()
                .getReceivableCoupons().stream()
                .map(this::toCouponDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{couponId}")
    public ResponseEntity<CouponDtoResponse> getCoupon(@PathVariable Integer couponId) {
        return orchestratorService.getCouponService()
                .getCoupon(new LitemallCouponId(couponId))
                .map(coupon -> ResponseEntity.ok(toCouponDto(coupon)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** My coupons, optionally filtered by status bucket (0 unused / 1 used / 2 expired). */
    @GetMapping("/my")
    public ResponseEntity<List<UserCouponDtoResponse>> getMyCoupons(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestParam(required = false) Integer status) {
        LitemallUserCouponStatus statusFilter =
                status != null ? LitemallUserCouponStatus.fromCode(status) : null;
        List<UserCouponDtoResponse> response = orchestratorService.getCouponService()
                .getMyCoupons(new LitemallUserId(userId), statusFilter).stream()
                .map(this::toUserCouponDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Usable-for-this-checkout: which of my coupons apply to a cart of
     * {@code amount} covering {@code goodsIds}/{@code categoryIds}. The caller
     * supplies the cart facts — promotion has no cart access.
     */
    @GetMapping("/usable")
    public ResponseEntity<List<CouponDtoResponse>> getUsableForCheckout(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) List<Integer> goodsIds,
            @RequestParam(required = false) List<Integer> categoryIds) {
        List<CouponDtoResponse> response = orchestratorService.getCouponService()
                .getUsableForCheckout(new LitemallUserId(userId), amount, goodsIds, categoryIds).stream()
                .map(this::toUsableCouponDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Wave 18 register-gift: grant every active TYPE_REGISTER coupon to the
     * given user. Fired by gateway-api once after successful registration
     * (machine token + X-User-Id), fail-silent edge-side; idempotent here via
     * the per-user claim limit.
     */
    @PostMapping("/register-gifts")
    public ResponseEntity<PromotionOperationDtoResponse> registerGifts(
            @RequestHeader("X-User-Id") Integer userId) {
        LitemallPromotionOperationResult result =
                orchestratorService.grantRegisterGifts(new LitemallUserId(userId));
        return buildResponse(result);
    }

    /** Exchange a redemption code for its coupon (dedicated exchange-by-code). */
    @PostMapping("/exchange")
    public ResponseEntity<PromotionOperationDtoResponse> exchangeCoupon(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestBody CouponExchangeRequest request) {
        LitemallExchangeCouponCommand command = new LitemallExchangeCouponCommand(
                new LitemallUserId(userId), request.getCode());
        LitemallPromotionOperationResult result = orchestratorService.exchangeCoupon(command);
        return buildResponse(result);
    }

    /** Release a redeemed coupon after its order failed (idempotent). */
    @PostMapping("/user/{userCouponId}/release")
    public ResponseEntity<PromotionOperationDtoResponse> releaseCoupon(
            @PathVariable Integer userCouponId,
            @RequestHeader("X-User-Id") Integer userId,
            @RequestBody CouponReleaseRequest request) {
        LitemallReleaseCouponCommand command = new LitemallReleaseCouponCommand(
                new LitemallUserCouponId(userCouponId),
                new LitemallUserId(userId),
                request.getOrderId());
        LitemallPromotionOperationResult result = orchestratorService.releaseCoupon(command);
        return buildResponse(result);
    }

    @PostMapping("/{couponId}/receive")
    public ResponseEntity<PromotionOperationDtoResponse> receiveCoupon(
            @PathVariable Integer couponId,
            @RequestHeader("X-User-Id") Integer userId,
            @RequestParam(required = false) String code) {

        LitemallReceiveCouponCommand command = new LitemallReceiveCouponCommand(
                new LitemallUserId(userId), new LitemallCouponId(couponId), code);
        LitemallPromotionOperationResult result = orchestratorService.receiveCoupon(command);
        return buildResponse(result);
    }

    @PostMapping("/user/{userCouponId}/redeem")
    public ResponseEntity<PromotionOperationDtoResponse> redeemCoupon(
            @PathVariable Integer userCouponId,
            @RequestHeader("X-User-Id") Integer userId,
            @RequestBody CouponRedeemRequest request) {

        LitemallRedeemCouponCommand command = new LitemallRedeemCouponCommand(
                new LitemallUserCouponId(userCouponId),
                new LitemallUserId(userId),
                request.getOrderId(),
                request.getOrderSubtotal());
        LitemallPromotionOperationResult result = orchestratorService.redeemCoupon(command);
        return buildResponse(result);
    }

    // ----- DTO mapping -----

    private CouponDtoResponse toCouponDto(LitemallCouponAggregate c) {
        return CouponDtoResponse.builder()
                .couponId(c.getCouponId() != null ? c.getCouponId().getId() : null)
                .name(c.getName())
                .description(c.getDescription())
                .tag(c.getTag())
                .discount(c.getDiscount() != null ? c.getDiscount().getAmount() : null)
                .discountType(c.getDiscountType() != null ? c.getDiscountType().getCode() : 0)
                .discountCap(c.getDiscountCap() != null ? c.getDiscountCap().getAmount() : null)
                .min(c.getMin() != null ? c.getMin().getAmount() : null)
                .type(c.getType() != null ? c.getType().getDisplayName() : null)
                .goodsType(c.getGoodsType() != null ? c.getGoodsType().getDisplayName() : null)
                .status(c.getStatus() != null ? c.getStatus().getDisplayName() : null)
                .startTime(c.getStartTime())
                .endTime(c.getEndTime())
                .build();
    }

    private CouponDtoResponse toUsableCouponDto(LitemallCouponServiceImpl.UsableCouponView view) {
        LitemallUserCouponAggregate held = view.getUserCoupon();
        LitemallCouponAggregate c = view.getCoupon();
        CouponDtoResponse dto = toCouponDto(c);
        dto.setUserCouponId(held.getUserCouponId() != null ? held.getUserCouponId().getId() : null);
        // The held instance's validity window overrides the definition's.
        dto.setStartTime(held.getStartTime());
        dto.setEndTime(held.getEndTime());
        // Wave 18: usable views carry the COMPUTED effective discount for the
        // passed cart amount; the raw percent rate moves to discountRate.
        if (c.isPercent()) {
            dto.setDiscountRate(c.getDiscount() != null ? c.getDiscount().getAmount() : null);
        }
        dto.setDiscount(view.getEffectiveDiscount());
        return dto;
    }

    private UserCouponDtoResponse toUserCouponDto(LitemallUserCouponAggregate u) {
        return UserCouponDtoResponse.builder()
                .userCouponId(u.getUserCouponId() != null ? u.getUserCouponId().getId() : null)
                .couponId(u.getCouponId() != null ? u.getCouponId().getId() : null)
                .status(u.getStatus() != null ? u.getStatus().getDisplayName() : null)
                .startTime(u.getStartTime())
                .endTime(u.getEndTime())
                .usedTime(u.getUsedTime())
                .orderId(u.getOrderId())
                .build();
    }
}
