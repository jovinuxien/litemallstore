package org.linlinjava.litemall.order.interfaces.dtos.cj.dispute;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.linlinjava.litemall.order.application.internal.cj.CjDisputeService;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute.CjDisputableLine;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute.CjDisputeQuote;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Payload for the customer's "report a problem" form: which items can be disputed,
 * the selectable reasons, the claim ceiling, and which expectations CJ allows.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class DisputeContextDtoResponse {

    private final List<Line> lines;
    private final List<Reason> reasons;
    private final BigDecimal maxAmountUsd;
    private final boolean refundAllowed;
    private final boolean reissueAllowed;

    private DisputeContextDtoResponse(List<Line> lines, List<Reason> reasons, BigDecimal maxAmountUsd,
                                      boolean refundAllowed, boolean reissueAllowed) {
        this.lines = lines;
        this.reasons = reasons;
        this.maxAmountUsd = maxAmountUsd;
        this.refundAllowed = refundAllowed;
        this.reissueAllowed = reissueAllowed;
    }

    public static DisputeContextDtoResponse fromDomain(CjDisputeService.DisputeContext context) {
        CjDisputeQuote quote = context.quote();
        return new DisputeContextDtoResponse(
                context.lines().stream().map(Line::fromDomain).collect(Collectors.toList()),
                quote.getReasons() == null ? List.of()
                        : quote.getReasons().stream()
                        .map(r -> new Reason(r.getId(), r.getName()))
                        .collect(Collectors.toList()),
                quote.getMaxAmountUsd(),
                quote.isRefundAllowed(),
                quote.isReissueAllowed());
    }

    @Getter
    public static class Line {
        private final String lineItemId;
        private final String productName;
        private final String imageUrl;
        private final BigDecimal unitPriceUsd;
        private final int maxQuantity;

        private Line(String lineItemId, String productName, String imageUrl,
                     BigDecimal unitPriceUsd, int maxQuantity) {
            this.lineItemId = lineItemId;
            this.productName = productName;
            this.imageUrl = imageUrl;
            this.unitPriceUsd = unitPriceUsd;
            this.maxQuantity = maxQuantity;
        }

        static Line fromDomain(CjDisputableLine line) {
            return new Line(line.getLineItemId(), line.getProductName(), line.getImageUrl(),
                    line.getUnitPriceUsd(), line.getMaxQuantity());
        }
    }

    @Getter
    public static class Reason {
        private final int id;
        private final String name;

        private Reason(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
