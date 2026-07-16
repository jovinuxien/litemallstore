package org.linlinjava.litemall.core.mail;

/**
 * The five shared customer-mail templates (Wave 6) — English, plain-text v1.
 * Pure rendering, no Spring: order's enqueue listeners render at enqueue time
 * (the outbox row stores the finished subject/body) and gateway-api reuses
 * {@link #passwordReset} from its edge-local reset-mail sender.
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

    private MailTemplates() {
    }

    public static RenderedMail orderConfirmation(String orderSn, String totalAmount) {
        String subject = "Your litemall order " + nz(orderSn) + " is confirmed";
        String body = "Thank you for your purchase!\n\n"
                + "We have received your payment for order " + nz(orderSn) + ".\n"
                + "Order total: " + nz(totalAmount) + "\n\n"
                + "We will let you know as soon as your order ships.\n\n"
                + FOOTER;
        return new RenderedMail(KEY_ORDER_CONFIRMATION, subject, body);
    }

    public static RenderedMail shipped(String orderSn, String shipChannel, String shipSn) {
        String subject = "Your litemall order " + nz(orderSn) + " has shipped";
        String body = "Good news — your order " + nz(orderSn) + " is on its way!\n\n"
                + "Carrier: " + nz(shipChannel) + "\n"
                + "Tracking number: " + nz(shipSn) + "\n\n"
                + "You can follow the shipment from the order detail page in your account.\n\n"
                + FOOTER;
        return new RenderedMail(KEY_SHIPPED, subject, body);
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
        String subject = "Reset your litemall password";
        String body = "We received a request to reset the password for your litemall account.\n\n"
                + "To choose a new password, open this link:\n"
                + nz(resetLink) + "\n\n"
                + "The link expires in " + expiryMinutes + " minutes and can be used once.\n"
                + "If you did not request a reset, you can safely ignore this email.\n\n"
                + FOOTER;
        return new RenderedMail(KEY_PASSWORD_RESET, subject, body);
    }

    private static final String FOOTER = "— The litemall team";

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
