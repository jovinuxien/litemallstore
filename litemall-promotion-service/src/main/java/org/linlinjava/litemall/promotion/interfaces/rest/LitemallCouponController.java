package org.linlinjava.litemall.promotion.interfaces.rest;

import org.linlinjava.litemall.promotion.application.LitemallPromotionOrchestratorService;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallUserCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallReceiveCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallRedeemCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.linlinjava.litemall.promotion.interfaces.dtos.CouponDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.CouponRedeemRequest;
import org.linlinjava.litemall.promotion.interfaces.dtos.PromotionOperationDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.UserCouponDtoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/my")
    public ResponseEntity<List<UserCouponDtoResponse>> getMyCoupons(@RequestHeader Integer userId) {
        List<UserCouponDtoResponse> response = orchestratorService.getCouponService()
                .getMyUsableCoupons(new LitemallUserId(userId)).stream()
                .map(this::toUserCouponDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{couponId}/receive")
    public ResponseEntity<PromotionOperationDtoResponse> receiveCoupon(
            @PathVariable Integer couponId,
            @RequestHeader Integer userId,
            @RequestParam(required = false) String code) {

        LitemallReceiveCouponCommand command = new LitemallReceiveCouponCommand(
                new LitemallUserId(userId), new LitemallCouponId(couponId), code);
        LitemallPromotionOperationResult result = orchestratorService.receiveCoupon(command);
        return buildResponse(result);
    }

    @PostMapping("/user/{userCouponId}/redeem")
    public ResponseEntity<PromotionOperationDtoResponse> redeemCoupon(
            @PathVariable Integer userCouponId,
            @RequestHeader Integer userId,
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
                .min(c.getMin() != null ? c.getMin().getAmount() : null)
                .type(c.getType() != null ? c.getType().getDisplayName() : null)
                .goodsType(c.getGoodsType() != null ? c.getGoodsType().getDisplayName() : null)
                .status(c.getStatus() != null ? c.getStatus().getDisplayName() : null)
                .startTime(c.getStartTime())
                .endTime(c.getEndTime())
                .build();
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
