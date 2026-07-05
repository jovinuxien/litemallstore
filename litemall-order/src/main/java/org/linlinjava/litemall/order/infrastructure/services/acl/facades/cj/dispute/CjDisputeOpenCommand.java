package org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute;

import lombok.Builder;
import lombok.Data;
import org.linlinjava.litemall.order.domain.model.valueobjects.cj.CjDisputeExpectation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Input to {@code CjDisputeFacade#open}: which CJ order/lines, why, what the customer
 * expects, and OUR idempotent {@code businessDisputeId}. The facade translates this to
 * CJ's create contract (expectType/refundType integers etc.).
 */
@Data
@Builder
public class CjDisputeOpenCommand {

    private String cjOrderId;
    /** Our unique merchant key — CJ dedupes creation on it (retry-safe). */
    private String businessDisputeId;
    private int reasonId;
    private CjDisputeExpectation expectation;
    private String message;
    private List<String> imageUrls;
    private List<Line> lines;

    @Data
    @Builder
    public static class Line {
        private String lineItemId;
        private int quantity;
        private BigDecimal unitPriceUsd;
    }
}
