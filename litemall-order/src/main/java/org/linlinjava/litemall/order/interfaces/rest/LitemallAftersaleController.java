package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.order.application.internal.aftersale.LitemallAftersaleService;
import org.linlinjava.litemall.order.application.util.exception.order.LitemallAftersaleException;
import org.linlinjava.litemall.order.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.interfaces.dtos.aftersale.AftersaleApplyRequestDto;
import org.linlinjava.litemall.order.interfaces.dtos.aftersale.AftersaleDtoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Customer aftersale/RMA endpoints for an order the caller OWNS: identity is always
 * the gateway-injected {@code X-User-Id} header (the submit/pay/cancel rule — never
 * a caller-supplied param), and a non-owner reads every path as "not found". Rule
 * violations surface as a clean {@code errno} envelope with the human-readable
 * reason, never a raw 500 — mirroring the CJ dispute controller.
 */
@RestController
@RequestMapping("/srv/order/{orderId}/aftersale")
public class LitemallAftersaleController {

    private static final int AFTERSALE_ERRNO = 730;

    private final LitemallAftersaleService aftersaleService;

    public LitemallAftersaleController(LitemallAftersaleService aftersaleService) {
        this.aftersaleService = aftersaleService;
    }

    /** Apply: refund-only or return-and-refund, with reason + optional amount/images. */
    @PostMapping
    public ApiResponse<AftersaleDtoResponse> apply(@PathVariable Integer orderId,
                                                   @RequestHeader("X-User-Id") Integer userId,
                                                   @RequestBody AftersaleApplyRequestDto request) {
        try {
            return ApiResponse.ok(AftersaleDtoResponse.fromDomain(aftersaleService.apply(
                    new LitemallUserId(userId), new LitemallOrderId(orderId),
                    new LitemallAftersaleService.ApplyCommand(
                            request.getType(), request.getReason(), request.getAmount(),
                            request.getPictures(), request.getComment()))));
        } catch (LitemallAftersaleException e) {
            return ApiResponse.fail(AFTERSALE_ERRNO, e.getMessage());
        }
    }

    /** The order's applications, newest first. */
    @GetMapping
    public ApiResponse<List<AftersaleDtoResponse>> list(@PathVariable Integer orderId,
                                                        @RequestHeader("X-User-Id") Integer userId) {
        try {
            return ApiResponse.ok(aftersaleService
                    .listForOrder(new LitemallUserId(userId), new LitemallOrderId(orderId)).stream()
                    .map(AftersaleDtoResponse::fromDomain)
                    .collect(Collectors.toList()));
        } catch (LitemallAftersaleException e) {
            return ApiResponse.fail(AFTERSALE_ERRNO, e.getMessage());
        }
    }

    /** One application's detail. */
    @GetMapping("/{aftersaleId}")
    public ApiResponse<AftersaleDtoResponse> detail(@PathVariable Integer orderId,
                                                    @PathVariable Integer aftersaleId,
                                                    @RequestHeader("X-User-Id") Integer userId) {
        try {
            return ApiResponse.ok(AftersaleDtoResponse.fromDomain(aftersaleService.detail(
                    new LitemallUserId(userId), new LitemallOrderId(orderId), aftersaleId)));
        } catch (LitemallAftersaleException e) {
            return ApiResponse.fail(AFTERSALE_ERRNO, e.getMessage());
        }
    }

    /** Withdraw a still-undecided application. */
    @PostMapping("/{aftersaleId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Integer orderId,
                                    @PathVariable Integer aftersaleId,
                                    @RequestHeader("X-User-Id") Integer userId) {
        try {
            aftersaleService.cancel(new LitemallUserId(userId), new LitemallOrderId(orderId), aftersaleId);
            return ApiResponse.ok(null);
        } catch (LitemallAftersaleException e) {
            return ApiResponse.fail(AFTERSALE_ERRNO, e.getMessage());
        }
    }
}
