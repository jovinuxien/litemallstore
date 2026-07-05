package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjDisputeException;
import org.linlinjava.litemall.order.domain.model.valueobjects.cj.CjDisputeResolution;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute.CjDisputableLine;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute.CjDisputeOpenCommand;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute.CjDisputeQuote;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute.CjDisputeSnapshot;
import org.linlinjava.litemall.order.infrastructure.services.cj.CjTokenService;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.CjDisputeFeignClient;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeBooleanResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeCancelRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeConfirmRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeConfirmResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeCreateRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeLine;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeListResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeProductsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * {@link CjDisputeFacade} implementation: authenticates via {@link CjTokenService}, maps
 * domain-language dispute operations onto the CJ dispute endpoints, and converts every
 * failure (transport, breaker-open, {@code result=false}) into
 * {@link LitemallCjDisputeException} carrying CJ's message. Mirrors
 * {@code CjDropshipOrderFacadeImpl}, including the ~1 QPS pacing after each CJ call.
 */
@Component
public class CjDisputeFacadeImpl implements CjDisputeFacade {

    private static final Logger log = LoggerFactory.getLogger(CjDisputeFacadeImpl.class);
    /** CJ getDisputeList page size; one order realistically has a handful of disputes. */
    private static final int LIST_PAGE_SIZE = 50;

    private final CjDisputeFeignClient disputeClient;
    private final CjTokenService cjTokenService;
    /**
     * Where CJ settles the merchant-side money on a refund resolution:
     * 1=CJ balance (default), 2=original platform. Customer money-back stays on the
     * local refund flow either way (decoupled v1).
     */
    private final int refundType;

    public CjDisputeFacadeImpl(CjDisputeFeignClient disputeClient, CjTokenService cjTokenService,
                               @Value("${spring.cjdropship.api.dispute-refund-type:1}") int refundType) {
        this.disputeClient = disputeClient;
        this.cjTokenService = cjTokenService;
        this.refundType = refundType;
    }

    @Override
    public List<CjDisputableLine> disputableLines(String cjOrderId) {
        CjDisputeProductsResponse response = call("disputeProducts",
                () -> disputeClient.disputeProducts(cjTokenService.getValidToken(), cjOrderId));
        if (!accepted(response.isResult(), response.getCode())) {
            throw new LitemallCjDisputeException(cjMessage("disputeProducts", response.getMessage()));
        }
        if (response.getData() == null || response.getData().getProductInfoList() == null) {
            return List.of();
        }
        return response.getData().getProductInfoList().stream()
                .map(p -> CjDisputableLine.builder()
                        .lineItemId(p.getLineItemId())
                        .cjVariantId(p.getCjVariantId())
                        .productName(p.getCjProductName())
                        .imageUrl(p.getCjImage())
                        .unitPriceUsd(p.getPrice())
                        .maxQuantity(p.getQuantity() == null ? 0 : p.getQuantity())
                        .disputable(Boolean.TRUE.equals(p.getCanChoose()))
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public CjDisputeQuote quote(String cjOrderId, List<CjDisputeOpenCommand.Line> lines) {
        CjDisputeConfirmRequest request = CjDisputeConfirmRequest.builder()
                .orderId(cjOrderId)
                .productInfoList(toWireLines(lines))
                .build();
        CjDisputeConfirmResponse response = call("disputeConfirmInfo",
                () -> disputeClient.disputeConfirmInfo(cjTokenService.getValidToken(), request));
        if (!accepted(response.isResult(), response.getCode()) || response.getData() == null) {
            throw new LitemallCjDisputeException(cjMessage("disputeConfirmInfo", response.getMessage()));
        }
        CjDisputeConfirmResponse.Payload data = response.getData();
        List<String> expectations = data.getExpectResultOptionList() == null
                ? List.of() : data.getExpectResultOptionList();
        return CjDisputeQuote.builder()
                .maxAmountUsd(data.getMaxAmount())
                .refundAllowed(expectations.contains("1"))
                .reissueAllowed(expectations.contains("2"))
                .reasons(data.getDisputeReasonList() == null ? List.of()
                        : data.getDisputeReasonList().stream()
                        .map(r -> CjDisputeQuote.Reason.builder()
                                .id(r.getDisputeReasonId() == null ? 0 : r.getDisputeReasonId())
                                .name(r.getReasonName())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    public void open(CjDisputeOpenCommand command) {
        CjDisputeCreateRequest request = CjDisputeCreateRequest.builder()
                .orderId(command.getCjOrderId())
                .businessDisputeId(command.getBusinessDisputeId())
                .disputeReasonId(command.getReasonId())
                .expectType((int) command.getExpectation().getCjCode())
                .refundType(refundType)
                .messageText(command.getMessage())
                .imageUrl(command.getImageUrls() == null || command.getImageUrls().isEmpty()
                        ? null : command.getImageUrls())
                .productInfoList(toWireLines(command.getLines()))
                .build();
        CjDisputeBooleanResponse response = call("create",
                () -> disputeClient.create(cjTokenService.getValidToken(), request));
        if (!accepted(response.isResult(), response.getCode()) || !Boolean.TRUE.equals(response.getData())) {
            throw new LitemallCjDisputeException(cjMessage("create", response.getMessage()));
        }
        log.info("CJ dispute opened for cjOrderId={} businessDisputeId={}",
                command.getCjOrderId(), command.getBusinessDisputeId());
    }

    @Override
    public void cancel(String cjOrderId, String cjDisputeId) {
        CjDisputeBooleanResponse response = call("cancel",
                () -> disputeClient.cancel(cjTokenService.getValidToken(),
                        new CjDisputeCancelRequest(cjOrderId, cjDisputeId)));
        if (!accepted(response.isResult(), response.getCode()) || !Boolean.TRUE.equals(response.getData())) {
            throw new LitemallCjDisputeException(cjMessage("cancel", response.getMessage()));
        }
        log.info("CJ dispute {} cancelled for cjOrderId={}", cjDisputeId, cjOrderId);
    }

    @Override
    public List<CjDisputeSnapshot> fetchDisputes(String cjOrderId) {
        CjDisputeListResponse response = call("getDisputeList",
                () -> disputeClient.getDisputeList(cjTokenService.getValidToken(), cjOrderId, 1, LIST_PAGE_SIZE));
        if (!accepted(response.isResult(), response.getCode())) {
            throw new LitemallCjDisputeException(cjMessage("getDisputeList", response.getMessage()));
        }
        if (response.getData() == null || response.getData().getList() == null) {
            return List.of();
        }
        return response.getData().getList().stream()
                .map(item -> CjDisputeSnapshot.builder()
                        .cjDisputeId(item.getId())
                        .status(item.getStatus())
                        .reasonName(item.getDisputeReason())
                        .resolution(CjDisputeResolution.fromCjCode(
                                item.getFinallyDeal() == null ? null : item.getFinallyDeal().shortValue()))
                        .refundAmountUsd(item.getMoney())
                        .resendOrderCode(item.getResendOrderCode())
                        .createDate(item.getCreateDate())
                        .build())
                .collect(Collectors.toList());
    }

    private static List<CjDisputeLine> toWireLines(List<CjDisputeOpenCommand.Line> lines) {
        return lines.stream()
                .map(l -> new CjDisputeLine(l.getLineItemId(), l.getQuantity(), l.getUnitPriceUsd()))
                .collect(Collectors.toList());
    }

    /** Success is CJ's result/code flag — a missing data object is judged per operation. */
    private static boolean accepted(boolean result, int code) {
        return result || code == 200;
    }

    private static String cjMessage(String operation, String message) {
        return "CJ dispute " + operation + " failed: " + (message == null ? "no message" : message);
    }

    /**
     * Run one CJ call, unwrapping to the root cause (the FeignErrorDecoder buries CJ's
     * message a few layers deep) and pacing afterwards — CJ's API is ~1 QPS account-wide
     * and dispute flows chain calls (products -> confirm, refresh -> cancel).
     */
    private <T> T call(String operation, Supplier<T> invocation) {
        try {
            return invocation.get();
        } catch (RuntimeException e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            throw new LitemallCjDisputeException(
                    cjMessage(operation, root.getMessage() == null ? "transport/auth failure" : root.getMessage()), e);
        } finally {
            try {
                Thread.sleep(1100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
