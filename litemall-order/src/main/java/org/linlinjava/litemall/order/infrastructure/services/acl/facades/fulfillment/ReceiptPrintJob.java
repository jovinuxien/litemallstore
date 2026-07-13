package org.linlinjava.litemall.order.infrastructure.services.acl.facades.fulfillment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything a receipt needs, detached from the order aggregate so printer adapters never
 * touch domain types (Wave 4, Task D). Rendered to text by {@code ReceiptRenderer}.
 *
 * <p>{@code originId} is the vendor-side exactly-once key (Yly dedupes on it): auto-print
 * uses the bare {@code orderSn}; the admin reprint appends {@code -R<epochMillis>} so a
 * deliberate reprint is never swallowed by the dedupe.
 */
@Data
@Builder
@AllArgsConstructor
public class ReceiptPrintJob {

    /** Vendor-side exactly-once key (Yly {@code origin_id}). */
    private String originId;
    private String orderSn;
    /** Shop header line, from {@code litemall.order.printer.business-name}. */
    private String businessName;
    /** {@code express} or {@code pickup} — pickup receipts print the verify code block. */
    private String deliveryType;
    /** Pickup verify code; null for courier orders (and pre-pay snapshots). */
    private String verifyCode;
    private String consignee;
    private String mobile;
    private LocalDateTime addTime;
    private LocalDateTime payTime;
    private List<Line> lines;
    private BigDecimal goodsPrice;
    private BigDecimal freightPrice;
    private BigDecimal couponPrice;
    private BigDecimal actualPrice;

    @Data
    @AllArgsConstructor
    public static class Line {
        private String goodsName;
        private int number;
        private BigDecimal price;
    }
}
