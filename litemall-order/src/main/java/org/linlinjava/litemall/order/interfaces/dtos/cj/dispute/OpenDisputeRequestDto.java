package org.linlinjava.litemall.order.interfaces.dtos.cj.dispute;

import lombok.Data;

import java.util.List;

/**
 * Customer request to open a dispute. {@code expectType} is the domain name
 * ("REFUND" | "REISSUE"); quantities are per selected line — prices are never taken
 * from the client (the service re-reads them from CJ).
 */
@Data
public class OpenDisputeRequestDto {

    private Integer reasonId;
    private String reasonName;
    private String expectType;
    private String message;
    private List<String> imageUrls;
    private List<Line> lines;

    @Data
    public static class Line {
        private String lineItemId;
        private Integer quantity;
    }
}
