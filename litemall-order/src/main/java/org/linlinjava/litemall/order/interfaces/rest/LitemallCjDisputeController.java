package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.order.application.internal.cj.CjDisputeService;
import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjDisputeException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCjDisputeAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.order.domain.model.valueobjects.cj.CjDisputeExpectation;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.interfaces.dtos.cj.dispute.DisputeContextDtoResponse;
import org.linlinjava.litemall.order.interfaces.dtos.cj.dispute.DisputeDtoResponse;
import org.linlinjava.litemall.order.interfaces.dtos.cj.dispute.OpenDisputeRequestDto;
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
 * Customer CJ dispute endpoints for a dropship order the caller OWNS: the identity is
 * always the gateway-injected {@code X-User-Id} header (same rule as submit/pay/cancel —
 * never a caller-supplied param). Guard failures and CJ rejections surface as a clean
 * {@code errno} envelope with the human-readable reason, never a raw 500.
 */
@RestController
@RequestMapping("/srv/order/{orderId}/disputes")
public class LitemallCjDisputeController {

    private static final int DISPUTE_ERRNO = 720;

    private final CjDisputeService disputeService;

    public LitemallCjDisputeController(CjDisputeService disputeService) {
        this.disputeService = disputeService;
    }

    /** The "report a problem" form data: disputable items, reasons, limits. */
    @GetMapping("/context")
    public ApiResponse<DisputeContextDtoResponse> context(@PathVariable Integer orderId,
                                                          @RequestHeader("X-User-Id") Integer userId) {
        try {
            return ApiResponse.ok(DisputeContextDtoResponse.fromDomain(
                    disputeService.getContext(new LitemallUserId(userId), new LitemallOrderId(orderId))));
        } catch (LitemallCjDisputeException e) {
            return ApiResponse.fail(DISPUTE_ERRNO, e.getMessage());
        }
    }

    /** Open a dispute for the order (one open dispute at a time). */
    @PostMapping
    public ApiResponse<DisputeDtoResponse> open(@PathVariable Integer orderId,
                                                @RequestHeader("X-User-Id") Integer userId,
                                                @RequestBody OpenDisputeRequestDto request) {
        try {
            CjDisputeExpectation expectation = parseExpectation(request.getExpectType());
            LitemallCjDisputeAggregate dispute = disputeService.openDispute(
                    new LitemallUserId(userId), new LitemallOrderId(orderId),
                    new CjDisputeService.OpenDisputeCommand(
                            request.getReasonId() == null ? 0 : request.getReasonId(),
                            request.getReasonName(),
                            expectation,
                            request.getMessage(),
                            request.getImageUrls(),
                            request.getLines() == null ? List.of()
                                    : request.getLines().stream()
                                    .map(l -> new CjDisputeService.OpenDisputeCommand.Line(
                                            l.getLineItemId(), l.getQuantity() == null ? 1 : l.getQuantity()))
                                    .collect(Collectors.toList())));
            return ApiResponse.ok(DisputeDtoResponse.fromDomain(dispute));
        } catch (LitemallCjDisputeException e) {
            return ApiResponse.fail(DISPUTE_ERRNO, e.getMessage());
        }
    }

    /** The order's disputes, refreshed from CJ when any is still open/unregistered. */
    @GetMapping
    public ApiResponse<List<DisputeDtoResponse>> list(@PathVariable Integer orderId,
                                                      @RequestHeader("X-User-Id") Integer userId) {
        try {
            return ApiResponse.ok(disputeService
                    .listDisputes(new LitemallUserId(userId), new LitemallOrderId(orderId)).stream()
                    .map(DisputeDtoResponse::fromDomain)
                    .collect(Collectors.toList()));
        } catch (LitemallCjDisputeException e) {
            return ApiResponse.fail(DISPUTE_ERRNO, e.getMessage());
        }
    }

    /** Withdraw an open dispute (cancelled at CJ too). */
    @PostMapping("/{disputeId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Integer orderId,
                                    @PathVariable Integer disputeId,
                                    @RequestHeader("X-User-Id") Integer userId) {
        try {
            disputeService.cancelDispute(new LitemallUserId(userId), new LitemallOrderId(orderId), disputeId);
            return ApiResponse.ok(null);
        } catch (LitemallCjDisputeException e) {
            return ApiResponse.fail(DISPUTE_ERRNO, e.getMessage());
        }
    }

    private static CjDisputeExpectation parseExpectation(String expectType) {
        if (expectType == null || expectType.isBlank()) {
            throw new LitemallCjDisputeException("Choose what you expect: a refund or a reissue");
        }
        try {
            return CjDisputeExpectation.valueOf(expectType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new LitemallCjDisputeException("Unknown expectation '" + expectType + "'");
        }
    }
}
