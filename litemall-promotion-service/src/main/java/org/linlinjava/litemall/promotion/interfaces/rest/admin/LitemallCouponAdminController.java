package org.linlinjava.litemall.promotion.interfaces.rest.admin;

import org.linlinjava.litemall.promotion.application.LitemallPromotionOrchestratorService;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallUserCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallGrantCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallIssueCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallUpdateCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.linlinjava.litemall.promotion.interfaces.dtos.CouponGrantRequest;
import org.linlinjava.litemall.promotion.interfaces.dtos.CouponManagerDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.PromotionOperationDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.UserCouponDtoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

import static org.linlinjava.litemall.promotion.interfaces.util.LitemallHttpResponseUtil.buildResponse;

/**
 * Admin coupon-management endpoints (crmeb {@code *ManagerResponse} surface).
 * Lives under {@code /srv/private/admin/**}, which litemall-svcsecurity gates to
 * {@code ROLE_ADMIN} (forwarded behind a valid machine token); the
 * {@link PreAuthorize} mirrors the sibling modules' intent. Gateway routing is a
 * gateway-worktree follow-up.
 */
@RestController
@RequestMapping("/srv/private/admin/promotion/coupon")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
public class LitemallCouponAdminController {

    private final LitemallPromotionOrchestratorService orchestratorService;

    public LitemallCouponAdminController(LitemallPromotionOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @GetMapping("/list")
    public ResponseEntity<List<CouponManagerDtoResponse>> listCoupons(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        List<CouponManagerDtoResponse> response = orchestratorService.getCouponService()
                .listCoupons(page, limit).stream()
                .map(this::toManagerDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{couponId}")
    public ResponseEntity<CouponManagerDtoResponse> getCoupon(@PathVariable Integer couponId) {
        return orchestratorService.getCouponService()
                .getCoupon(new LitemallCouponId(couponId))
                .map(coupon -> ResponseEntity.ok(toManagerDto(coupon)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PromotionOperationDtoResponse> issueCoupon(
            @RequestBody LitemallIssueCouponCommand command) {
        LitemallPromotionOperationResult result = orchestratorService.issueCoupon(command);
        return buildResponse(result);
    }

    @PutMapping("/{couponId}")
    public ResponseEntity<PromotionOperationDtoResponse> updateCoupon(
            @PathVariable Integer couponId,
            @RequestBody LitemallUpdateCouponCommand command) {
        LitemallPromotionOperationResult result =
                orchestratorService.updateCoupon(new LitemallCouponId(couponId), command);
        return buildResponse(result);
    }

    @DeleteMapping("/{couponId}")
    public ResponseEntity<PromotionOperationDtoResponse> deleteCoupon(@PathVariable Integer couponId) {
        LitemallPromotionOperationResult result =
                orchestratorService.deleteCoupon(new LitemallCouponId(couponId));
        return buildResponse(result);
    }

    /** Issuance records: who holds this coupon and in what state. */
    @GetMapping("/{couponId}/users")
    public ResponseEntity<List<UserCouponDtoResponse>> listIssueRecords(
            @PathVariable Integer couponId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        List<UserCouponDtoResponse> response = orchestratorService.getCouponService()
                .listIssueRecords(new LitemallCouponId(couponId), page, limit).stream()
                .map(this::toIssueRecordDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /** Direct-grant: push a coupon into a specific user's wallet. */
    @PostMapping("/grant")
    public ResponseEntity<PromotionOperationDtoResponse> grantCoupon(
            @RequestBody CouponGrantRequest request) {
        LitemallGrantCouponCommand command = new LitemallGrantCouponCommand(
                new LitemallCouponId(request.getCouponId()),
                new LitemallUserId(request.getUserId()));
        LitemallPromotionOperationResult result = orchestratorService.grantCoupon(command);
        return buildResponse(result);
    }

    private UserCouponDtoResponse toIssueRecordDto(LitemallUserCouponAggregate u) {
        return UserCouponDtoResponse.builder()
                .userCouponId(u.getUserCouponId() != null ? u.getUserCouponId().getId() : null)
                .couponId(u.getCouponId() != null ? u.getCouponId().getId() : null)
                .userId(u.getUserId() != null ? u.getUserId().getId() : null)
                .status(u.getStatus() != null ? u.getStatus().getDisplayName() : null)
                .startTime(u.getStartTime())
                .endTime(u.getEndTime())
                .usedTime(u.getUsedTime())
                .orderId(u.getOrderId())
                .build();
    }

    private CouponManagerDtoResponse toManagerDto(LitemallCouponAggregate c) {
        return CouponManagerDtoResponse.builder()
                .couponId(c.getCouponId() != null ? c.getCouponId().getId() : null)
                .name(c.getName())
                .description(c.getDescription())
                .tag(c.getTag())
                .total(c.getTotal())
                .discount(c.getDiscount() != null ? c.getDiscount().getAmount() : null)
                .min(c.getMin() != null ? c.getMin().getAmount() : null)
                .limitPerUser(c.getLimitPerUser())
                .type(c.getType() != null ? c.getType().getDisplayName() : null)
                .status(c.getStatus() != null ? c.getStatus().getDisplayName() : null)
                .goodsType(c.getGoodsType() != null ? c.getGoodsType().getDisplayName() : null)
                .goodsValue(c.getGoodsValue())
                .code(c.getCode())
                .timeType(c.getTimeType() != null ? c.getTimeType().getDisplayName() : null)
                .days(c.getDays())
                .startTime(c.getStartTime())
                .endTime(c.getEndTime())
                .build();
    }
}
