package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * CJ {@code disputes/getDisputeList} response: paged disputes for a CJ order, carrying
 * CJ's dispute id, status string, and the final resolution ({@code finallyDeal}:
 * 1=refund, 2=reissue, 3=reject) once decided.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CjDisputeListResponse {

    private int code;
    private boolean result;
    private String message;
    private String requestId;
    private Payload data;

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        private Integer pageNum;
        private Integer pageSize;
        private Integer total;
        private List<Item> list;
    }

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String id;
        private String status;
        private String disputeReason;
        private BigDecimal replacementAmount;
        private BigDecimal money;
        private String resendOrderCode;
        private Integer finallyDeal;
        private String createDate;
    }
}
