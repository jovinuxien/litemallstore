package org.linlinjava.litemall.core.mail;

import java.util.List;

/**
 * The five shared customer-mail templates (Wave 6) — English, plain-text v1;
 * Trovemo-branded copy since Wave 10. Pure rendering, no Spring: order's
 * enqueue listeners render at enqueue time (the outbox row stores the finished
 * subject/body) and gateway-api mirrors {@link #passwordReset} in its
 * edge-local reset-mail sender.
 *
 * <p>Template keys are the cross-service contract (outbox rows, admin panel
 * filters, the gateway-admin handoff) — do not rename.
 */
public final class MailTemplates {

    public static final String KEY_ORDER_CONFIRMATION = "order-confirmation";
    public static final String KEY_SHIPPED = "shipped";
    public static final String KEY_REFUND_APPROVED = "refund-approved";
    public static final String KEY_PICKUP_CODE = "pickup-code";
    public static final String KEY_PASSWORD_RESET = "password-reset";

    /** A rendered mail: the template key plus the finished subject and plain-text body. */
    public record RenderedMail(String templateKey, String subject, String body) {
    }

    /** One purchased line in the confirmation mail; price is the already-formatted unit price. */
    public record OrderLine(String name, String specifications, int quantity, String price) {
    }

    /**
     * Everything the rich confirmation body needs, pre-formatted by the caller
     * (money strings include their currency symbol; blank/null amount = omit
     * that line; {@code deliveryBlock} is the finished multi-line address or
     * pickup text).
     */
    public record OrderConfirmationDetails(String orderSn,
                                           String payTime,
                                           List<OrderLine> lines,
                                           String goodsPrice,
                                           String freightPrice,
                                           String couponPrice,
                                           String taxPrice,
                                           String actualPrice,
                                           String deliveryBlock) {
    }

    private MailTemplates() {
    }

    /**
     * Minimal confirmation (orderSn + total only) — the FALLBACK body used when
     * the full order data cannot be loaded at enqueue time. The rich variant is
     * {@link #orderConfirmation(OrderConfirmationDetails)}.
     */
    public static RenderedMail orderConfirmation(String orderSn, String totalAmount) {
        String subject = confirmationSubject(orderSn);
        String body = "Thank you for your purchase!\n\n"
                + "We have received your payment for order " + nz(orderSn) + ".\n"
                + "Order total: " + nz(totalAmount) + "\n\n"
                + SHIP_SOON + "\n\n"
                + FOOTER;
        return new RenderedMail(KEY_ORDER_CONFIRMATION, subject, body);
    }

    /** Full confirmation: line items, amounts breakdown and delivery details. (Wave 10) */
    public static RenderedMail orderConfirmation(OrderConfirmationDetails details) {
        StringBuilder body = new StringBuilder();
        body.append("Thank you for your purchase!\n\n")
                .append("We have received your payment for order ").append(nz(details.orderSn())).append(".\n");
        if (notBlank(details.payTime())) {
            body.append("Paid at: ").append(details.payTime()).append('\n');
        }

        List<OrderLine> lines = details.lines();
        if (lines != null && !lines.isEmpty()) {
            body.append('\n').append(section("Your items"));
            for (OrderLine line : lines) {
                body.append("- ").append(nz(line.name()));
                if (notBlank(line.specifications())) {
                    body.append(" (").append(line.specifications()).append(')');
                }
                body.append(" x ").append(line.quantity());
                if (notBlank(line.price())) {
                    body.append(" — ").append(line.price());
                }
                body.append('\n');
            }
        }

        body.append('\n').append(section("Amounts"));
        amountLine(body, "Items subtotal:", details.goodsPrice());
        amountLine(body, "Shipping:", details.freightPrice());
        amountLine(body, "Coupon discount:", details.couponPrice());
        amountLine(body, "Tax:", details.taxPrice());
        amountLine(body, "Order total:", details.actualPrice());

        if (notBlank(details.deliveryBlock())) {
            body.append('\n').append(section("Delivery")).append(details.deliveryBlock()).append('\n');
        }

        body.append('\n').append(SHIP_SOON).append("\n\n").append(FOOTER);
        return new RenderedMail(KEY_ORDER_CONFIRMATION, confirmationSubject(details.orderSn()), body.toString());
    }

    public static RenderedMail shipped(String orderSn, String shipChannel, String shipSn) {
        String subject = "Your Trovemo order " + nz(orderSn) + " has shipped";
        StringBuilder body = new StringBuilder("Good news — your order " + nz(orderSn) + " is on its way!\n\n");
        if (notBlank(shipChannel)) {
            body.append("Carrier: ").append(shipChannel).append('\n');
        }
        // CJ sometimes reports SHIPPED before assigning the tracking number; never
        // render a blank "Tracking number:" line — promise the follow-up instead.
        if (notBlank(shipSn)) {
            body.append("Tracking number: ").append(shipSn).append('\n');
        } else {
            body.append("Your tracking number is being assigned — we will send it in a follow-up email.\n");
        }
        body.append("\nYou can follow the shipment from the order detail page in your account.\n\n").append(FOOTER);
        return new RenderedMail(KEY_SHIPPED, subject, body.toString());
    }

    public static RenderedMail refundApproved(String orderSn, String refundAmount) {
        String subject = "Your refund for order " + nz(orderSn) + " has been approved";
        String body = "Your after-sale request for order " + nz(orderSn) + " has been approved.\n\n"
                + "Refund amount: " + nz(refundAmount) + "\n\n"
                + "The refund has been issued to your original payment method or account balance.\n\n"
                + FOOTER;
        return new RenderedMail(KEY_REFUND_APPROVED, subject, body);
    }

    public static RenderedMail pickupCode(String orderSn, String pickupLocation, String verifyCode) {
        String subject = "Your pickup code for order " + nz(orderSn);
        String body = "Your order " + nz(orderSn) + " is paid and ready for in-store pickup.\n\n"
                + "Pickup location: " + nz(pickupLocation) + "\n"
                + "Pickup code: " + nz(verifyCode) + "\n\n"
                + "Show this code at the counter to collect your order.\n\n"
                + FOOTER;
        return new RenderedMail(KEY_PICKUP_CODE, subject, body);
    }

    public static RenderedMail passwordReset(String resetLink, long expiryMinutes) {
        String subject = "Reset your Trovemo password";
        String body = "We received a request to reset the password for your Trovemo account.\n\n"
                + "To choose a new password, open this link:\n"
                + nz(resetLink) + "\n\n"
                + "The link expires in " + expiryMinutes + " minutes and can be used once.\n"
                + "If you did not request a reset, you can safely ignore this email.\n\n"
                + FOOTER;
        return new RenderedMail(KEY_PASSWORD_RESET, subject, body);
    }

    private static final String FOOTER = "— The Trovemo team";
    private static final String SHIP_SOON = "We will let you know as soon as your order ships.";

    private static String confirmationSubject(String orderSn) {
        return "Your Trovemo order " + nz(orderSn) + " is confirmed";
    }

    private static String section(String title) {
        return title + "\n" + "-".repeat(title.length()) + "\n";
    }

    /** One right-padded label + amount line; a blank amount omits the line entirely. */
    private static void amountLine(StringBuilder body, String label, String amount) {
        if (notBlank(amount)) {
            body.append(String.format("%-16s %s", label, amount)).append('\n');
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
