package org.linlinjava.litemall.order.infrastructure.acl.printer;

import org.linlinjava.litemall.order.infrastructure.services.acl.facades.fulfillment.ReceiptPrintJob;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * The ONE receipt template (Wave 4, Task D), shared by the logging adapter and the Yly
 * adapter so what dev logs is exactly what the cloud printer prints. English only (the
 * dev DB is de-Chinesed), ~32-character monospace thermal-paper style:
 *
 * <pre>
 *          litemall
 * --------------------------------
 * Order: 20260713xxxxxx
 * Paid:  2026-07-13 10:15:00
 * --------------------------------
 * Item name truncated..  2 x 9.99
 * --------------------------------
 * Goods:                    19.98
 * Freight:                   5.00
 * Coupon:                   -2.00
 * TOTAL:                    22.98
 * --------------------------------
 * PICKUP CODE: 0123456789      (pickup orders only)
 * Consignee: Jane Doe
 * Mobile:    070-1234567
 * </pre>
 *
 * <p>Public (not package-private) because the Yly adapter lives in the {@code .yly}
 * subpackage and Java packages are not hierarchical; still printer-internal by
 * convention — nothing outside {@code infrastructure/acl/printer} should render receipts.
 */
public final class ReceiptRenderer {

    static final int WIDTH = 32;
    private static final String SEPARATOR = "-".repeat(WIDTH);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** {@code delivery_type} of an in-store pickup order (mirrors LitemallOrderAggregate). */
    private static final String DELIVERY_PICKUP = "pickup";

    private ReceiptRenderer() {
    }

    public static String render(ReceiptPrintJob job) {
        StringBuilder sb = new StringBuilder(512);
        sb.append(center(nonBlank(job.getBusinessName(), "litemall"))).append('\n');
        sb.append(SEPARATOR).append('\n');
        sb.append("Order: ").append(nonBlank(job.getOrderSn(), "?")).append('\n');
        if (job.getPayTime() != null) {
            sb.append("Paid:  ").append(DT.format(job.getPayTime())).append('\n');
        } else if (job.getAddTime() != null) {
            sb.append("Placed: ").append(DT.format(job.getAddTime())).append('\n');
        }
        sb.append(SEPARATOR).append('\n');
        if (job.getLines() != null) {
            for (ReceiptPrintJob.Line line : job.getLines()) {
                String qtyPrice = line.getNumber() + " x " + money(line.getPrice());
                String name = truncate(nonBlank(line.getGoodsName(), "item"),
                        Math.max(1, WIDTH - qtyPrice.length() - 2));
                sb.append(padLine(name, qtyPrice)).append('\n');
            }
        }
        sb.append(SEPARATOR).append('\n');
        sb.append(padLine("Goods:", money(job.getGoodsPrice()))).append('\n');
        sb.append(padLine("Freight:", money(job.getFreightPrice()))).append('\n');
        if (job.getCouponPrice() != null && job.getCouponPrice().signum() > 0) {
            sb.append(padLine("Coupon:", "-" + money(job.getCouponPrice()))).append('\n');
        }
        sb.append(padLine("TOTAL:", money(job.getActualPrice()))).append('\n');
        sb.append(SEPARATOR).append('\n');
        if (DELIVERY_PICKUP.equals(job.getDeliveryType())) {
            sb.append("PICKUP CODE: ").append(nonBlank(job.getVerifyCode(), "(pending)")).append('\n');
        }
        if (job.getConsignee() != null && !job.getConsignee().isBlank()) {
            sb.append("Consignee: ").append(job.getConsignee()).append('\n');
        }
        if (job.getMobile() != null && !job.getMobile().isBlank()) {
            sb.append("Mobile:    ").append(job.getMobile()).append('\n');
        }
        return sb.toString();
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "0.00" : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 2)) + "..";
    }

    /** Left text + right text on one ~32-char line, right-aligned amount, min one space between. */
    private static String padLine(String left, String right) {
        int pad = WIDTH - left.length() - right.length();
        return left + " ".repeat(Math.max(1, pad)) + right;
    }

    private static String center(String text) {
        String t = truncate(text, WIDTH);
        int pad = (WIDTH - t.length()) / 2;
        return " ".repeat(Math.max(0, pad)) + t;
    }
}
