package org.linlinjava.litemall.promotion.interfaces.rest.admin;

import org.linlinjava.litemall.db.domain.LitemallCouponDelivery;
import org.linlinjava.litemall.promotion.application.LitemallPromotionOrchestratorService;
import org.linlinjava.litemall.promotion.application.internal.CouponDeliveryServiceImpl;
import org.linlinjava.litemall.promotion.application.internal.CouponDeliveryServiceImpl.CouponDeliveryException;
import org.linlinjava.litemall.promotion.application.internal.CouponDeliveryServiceImpl.DeliverResult;
import org.linlinjava.litemall.promotion.application.internal.CouponDeliveryServiceImpl.PerformanceView;
import org.linlinjava.litemall.promotion.application.internal.CouponDeliveryServiceImpl.SegmentCriteria;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallUserCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallGrantCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallIssueCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallUpdateCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallCouponDeliveryRepository.DeliveryPage;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.linlinjava.litemall.promotion.interfaces.dtos.CouponDeliverRequest;
import org.linlinjava.litemall.promotion.interfaces.dtos.CouponGrantRequest;
import org.linlinjava.litemall.promotion.interfaces.dtos.CouponManagerDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.PromotionOperationDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.UserCouponDtoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** Explicit ISO strings — this module must never leak LocalDateTime array serialization. */
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final LitemallPromotionOrchestratorService orchestratorService;
    private final CouponDeliveryServiceImpl deliveryService;

    public LitemallCouponAdminController(LitemallPromotionOrchestratorService orchestratorService,
                                         CouponDeliveryServiceImpl deliveryService) {
        this.orchestratorService = orchestratorService;
        this.deliveryService = deliveryService;
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

    // ------------------------------------------------------------------
    // Wave 22: targeted delivery + measurement (errno envelope)
    // ------------------------------------------------------------------

    /**
     * Deliver the coupon to an RFM segment over paid orders. Body
     * {@code {recencyDays?, minFrequency?, minMonetary?, preview?}} — at least
     * one criterion required (402); {@code preview:true} returns
     * {@code {matched}} only, zero side effects; otherwise grants through the
     * existing direct-grant path (idempotent via the per-user claim limit) and
     * returns {@code {matched, granted, skipped}}. Typed refusals: 770 unknown
     * coupon, 771 expired/withdrawn, 772 audience above the sweep cap.
     */
    @PostMapping("/{couponId}/deliver")
    public ApiResponse<Map<String, Object>> deliver(@PathVariable Integer couponId,
                                                    @RequestBody CouponDeliverRequest request) {
        try {
            SegmentCriteria criteria = new SegmentCriteria(
                    request.getRecencyDays(), request.getMinFrequency(), request.getMinMonetary());
            boolean preview = Boolean.TRUE.equals(request.getPreview());
            DeliverResult result = deliveryService.deliver(couponId, criteria, preview);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("matched", result.matched());
            if (!preview) {
                body.put("granted", result.granted());
                body.put("skipped", result.skipped());
            }
            return ApiResponse.ok(body);
        } catch (CouponDeliveryException e) {
            return ApiResponse.fail(e.getErrno(), e.getMessage());
        }
    }

    /** Conversion measurement; null (never 0) redemptionPct/avgOrderValue when undefined. */
    @GetMapping("/{couponId}/performance")
    public ApiResponse<Map<String, Object>> performance(@PathVariable Integer couponId) {
        try {
            PerformanceView view = deliveryService.performance(couponId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("granted", view.granted());
            body.put("used", view.used());
            body.put("redemptionPct", view.redemptionPct());
            body.put("ordersCount", view.ordersCount());
            body.put("revenue", view.revenue());
            body.put("avgOrderValue", view.avgOrderValue());
            return ApiResponse.ok(body);
        } catch (CouponDeliveryException e) {
            return ApiResponse.fail(e.getErrno(), e.getMessage());
        }
    }

    /** Delivery history page, newest first; {@code couponId} omitted = all coupons. */
    @GetMapping("/deliveries")
    public ApiResponse<Map<String, Object>> deliveries(
            @RequestParam(required = false) Integer couponId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        DeliveryPage result = deliveryService.deliveries(couponId, page, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", result.total());
        body.put("page", page);
        body.put("limit", limit);
        body.put("list", result.rows().stream().map(this::toDeliveryDto).collect(Collectors.toList()));
        return ApiResponse.ok(body);
    }

    private Map<String, Object> toDeliveryDto(LitemallCouponDelivery d) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("deliveryId", d.getId());
        dto.put("couponId", d.getCouponId());
        dto.put("segmentJson", d.getSegmentJson());
        dto.put("matched", d.getMatched());
        dto.put("granted", d.getGranted());
        dto.put("skipped", d.getSkipped());
        dto.put("addTime", d.getAddTime() != null ? d.getAddTime().format(ISO) : null);
        return dto;
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
                .discountType(c.getDiscountType() != null ? c.getDiscountType().getCode() : 0)
                .discountCap(c.getDiscountCap() != null ? c.getDiscountCap().getAmount() : null)
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
