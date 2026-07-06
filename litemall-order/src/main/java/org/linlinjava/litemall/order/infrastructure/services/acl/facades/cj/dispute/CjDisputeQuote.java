package org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a dispute over the selected lines may claim, in domain language: the selectable
 * reasons, the maximum claimable USD amount, and which expectations CJ allows.
 */
@Data
@Builder
public class CjDisputeQuote {

    private BigDecimal maxAmountUsd;
    private boolean refundAllowed;
    private boolean reissueAllowed;
    private List<Reason> reasons;

    @Data
    @Builder
    public static class Reason {
        private int id;
        private String name;
    }
}
