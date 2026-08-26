package org.linlinjava.litemall.core.mail;

import java.util.List;

/**
 * HTML variants (V48) of the customer-mail templates in {@link MailTemplates}.
 * Pure rendering, no Spring: the order enqueue listener renders at enqueue time
 * and stores the result in {@code litemall_mail_outbox.body_html}, next to the
 * plain-text body that stays the multipart fallback (and the admin-panel view).
 *
 * <p>Email-client constraints, deliberately: table layout, inline styles only,
 * absolute image URLs, no external CSS/JS, max-width 600.
 *
 * <p><b>Design system.</b> Every colour, face and radius below is a constant
 * mirroring the storefront SPA's own tokens (the {@code --lm-*} custom
 * properties in {@code product-card.scss} / {@code global.scss} /
 * {@code layout-header.scss}) so a mail reads as the same product as the site.
 * Style literals are never inlined at a call site — that is exactly how the
 * first two templates drifted from the storefront and from each other. Change a
 * token here and every template moves together.
 *
 * <p>Three client-compatibility rules are load-bearing, not taste:
 * <ul>
 *   <li>every text node carries an explicit {@code font-family} — Outlook's Word
 *       engine does not inherit it from {@code <body>} and falls back to Times;</li>
 *   <li>each template opens with a hidden preheader — otherwise the inbox
 *       preview line shows whatever body text happens to come first;</li>
 *   <li>the shell declares {@code color-scheme: light} so Apple Mail and Outlook
 *       dark mode stop force-inverting the card and the brand band.</li>
 * </ul>
 *
 * <p>Every dynamic value is HTML-escaped here — callers pass raw strings.
 * Money strings arrive pre-formatted (symbol included), blank = omit the line,
 * exactly like the plain-text templates.
 */
public final class MailHtmlTemplates {

    // ------------------------------------------------------------------
    // Design tokens — the storefront's own values, single source of truth
    // ------------------------------------------------------------------

    /** Storefront face stack (global.scss --lm-font); mail clients fall back down it as the site does. */
    private static final String FONT = "'Amazon Ember','Helvetica Neue',Helvetica,Arial,'Segoe UI',Roboto,sans-serif";
    private static final String MONO = "Consolas,Menlo,'Courier New',monospace";

    private static final String PRIMARY = "#0e7c86";        // --lm-primary
    private static final String PRIMARY_DARK = "#0a5d65";   // --lm-primary-dark (the site header band)
    private static final String PRIMARY_SOFT = "#e3f2f3";   // --lm-primary-soft
    private static final String PRIMARY_SOFT_EDGE = "#cfe7e9";
    private static final String SUCCESS = "#1f9d6b";        // --lm-success
    private static final String TEXT = "#1f2a2e";           // --lm-text
    private static final String MUTED = "#6b7b82";          // --lm-muted
    private static final String BG = "#f6f9fa";             // --lm-bg
    private static final String SURFACE = "#ffffff";        // --lm-surface
    private static final String BORDER = "#eceff1";         // --lm-border

    private static final String RADIUS_CARD = "10px";       // --lm-radius
    private static final String RADIUS_SM = "6px";
    private static final String WIDTH = "600";

    /** Support address shown in every footer; mirrors the storefront's own contact surfaces. */
    private static final String SUPPORT_EMAIL = "support@trovemo.com";

    /** One purchased line; {@code imageUrl} must be absolute (or blank = no thumbnail). */
    public record HtmlOrderLine(String name, String specifications, int quantity, String price, String imageUrl) {
    }

    /**
     * Everything the confirmation HTML needs. {@code deliveryLines} is the
     * address/pickup block one display line per entry; {@code orderUrl} and
     * {@code logoUrl} must be absolute (blank logo = text wordmark fallback).
     */
    public record OrderConfirmationHtml(String orderSn,
                                        String payTime,
                                        List<HtmlOrderLine> lines,
                                        String goodsPrice,
                                        String freightPrice,
                                        String couponPrice,
                                        String taxPrice,
                                        String actualPrice,
                                        List<String> deliveryLines,
                                        String orderUrl,
                                        String logoUrl) {
    }

    private MailHtmlTemplates() {
    }

    // ------------------------------------------------------------------
    // Templates
    // ------------------------------------------------------------------

    public static String orderConfirmation(OrderConfirmationHtml d) {
        StringBuilder main = new StringBuilder();
        main.append(heading("Thank you for your purchase!"));
        main.append(paragraph("We have received your payment for order <strong>" + esc(d.orderSn()) + "</strong>."
                + (notBlank(d.payTime()) ? " Paid at " + esc(d.payTime()) + "." : "")));

        if (d.lines() != null && !d.lines().isEmpty()) {
            main.append(sectionTitle("Your items"));
            main.append(openTable());
            for (HtmlOrderLine line : d.lines()) {
                main.append(itemRow(line));
            }
            main.append("</table>");
        }

        main.append(sectionTitle("Order summary"));
        main.append(openTable());
        amountRow(main, "Items subtotal", d.goodsPrice(), false);
        amountRow(main, "Shipping", d.freightPrice(), false);
        // A discount is money saved: the storefront's success green, not the body grey.
        amountRow(main, "Coupon discount", d.couponPrice(), false, SUCCESS);
        amountRow(main, "Tax", d.taxPrice(), false);
        amountRow(main, "Order total", d.actualPrice(), true);
        main.append("</table>");

        if (d.deliveryLines() == null || d.deliveryLines().isEmpty()) {
            // Nothing follows the totals to open the gap the next section title would
            // have given: the closing line must not crowd the grand total.
            main.append(spacer());
        } else {
            main.append(sectionTitle("Delivery"));
            StringBuilder inner = new StringBuilder();
            for (String line : d.deliveryLines()) {
                if (notBlank(line)) {
                    inner.append(panelLine(esc(line)));
                }
            }
            main.append(panel(inner.toString(), false));
        }

        main.append(paragraph("We will let you know as soon as your order ships."));
        main.append(button(d.orderUrl(), "View your order"));

        String preheader = notBlank(d.actualPrice())
                ? "Order " + nz(d.orderSn()) + " is confirmed — " + d.actualPrice()
                : "Order " + nz(d.orderSn()) + " is confirmed";
        return shell("Order " + nz(d.orderSn()) + " confirmed", preheader, d.logoUrl(), main.toString());
    }

    /** Blank {@code trackingNumber} = shipped-without-tracking copy (CJ sometimes lags the number). */
    public static String shipped(String orderSn, String carrier, String trackingNumber, String orderUrl, String logoUrl) {
        StringBuilder main = new StringBuilder();
        main.append(heading("Your order is on its way!"));
        main.append(paragraph("Good news — your order <strong>" + esc(orderSn) + "</strong> has shipped."));

        StringBuilder inner = new StringBuilder();
        if (notBlank(carrier)) {
            inner.append(panelLine("Carrier: <strong>" + esc(carrier) + "</strong>"));
        }
        if (notBlank(trackingNumber)) {
            inner.append(panelLine("Tracking number: <strong style=\"font-family:" + MONO + ";\">"
                    + esc(trackingNumber) + "</strong>"));
        } else {
            inner.append(panelLine("Your tracking number is being assigned — we will send it in a follow-up email."));
        }
        main.append(panel(inner.toString(), true));

        main.append(paragraph("You can follow the shipment from the order detail page in your account."));
        main.append(button(orderUrl, "Track your order"));

        String preheader = notBlank(trackingNumber)
                ? "Order " + nz(orderSn) + " shipped — tracking " + trackingNumber
                : "Order " + nz(orderSn) + " has shipped";
        return shell("Order " + nz(orderSn) + " shipped", preheader, logoUrl, main.toString());
    }

    /**
     * In-store pickup code. The code is the payload of this mail, so it gets the
     * emphasised figure treatment; a blank code degrades to the "check your order
     * page" line rather than rendering an empty box.
     */
    public static String pickupCode(String orderSn, String pickupLocation, String verifyCode,
                                    String orderUrl, String logoUrl) {
        StringBuilder main = new StringBuilder();
        main.append(heading("Your order is ready for pickup"));
        main.append(paragraph("Order <strong>" + esc(orderSn) + "</strong> is paid and waiting for you."));

        if (notBlank(verifyCode)) {
            main.append(figure("Pickup code", verifyCode, true));
            main.append(paragraph("Show this code at the counter to collect your order."));
        } else {
            main.append(paragraph("Your pickup code is being issued — you can also find it on your order page."));
        }

        if (notBlank(pickupLocation)) {
            main.append(sectionTitle("Pickup location"));
            main.append(panel(panelLine(esc(pickupLocation)), false));
        }

        main.append(button(orderUrl, "View your order"));

        String preheader = notBlank(verifyCode)
                ? "Pickup code " + verifyCode + " for order " + nz(orderSn)
                : "Order " + nz(orderSn) + " is ready for pickup";
        return shell("Pickup code for order " + nz(orderSn), preheader, logoUrl, main.toString());
    }

    /**
     * Refund approved. {@code refundAmount} arrives pre-formatted with its symbol;
     * blank = the amount could not be resolved, so the copy states the refund
     * without naming a figure rather than printing an empty one.
     */
    public static String refundApproved(String orderSn, String refundAmount, String orderUrl, String logoUrl) {
        StringBuilder main = new StringBuilder();
        main.append(heading("Your refund has been approved"));
        main.append(paragraph("We have approved the after-sale request for order <strong>"
                + esc(orderSn) + "</strong>."));

        if (notBlank(refundAmount)) {
            main.append(figure("Refund amount", refundAmount, false));
        }
        main.append(paragraph("The refund has been issued to your original payment method or account balance. "
                + "Depending on your bank it can take a few working days to appear on your statement."));
        main.append(button(orderUrl, "View your order"));

        String preheader = notBlank(refundAmount)
                ? "Refund of " + refundAmount + " approved for order " + nz(orderSn)
                : "Your refund for order " + nz(orderSn) + " has been approved";
        return shell("Refund approved for order " + nz(orderSn), preheader, logoUrl, main.toString());
    }

    // ------------------------------------------------------------------
    // Building blocks
    // ------------------------------------------------------------------

    /** The shared 600px card: brand band, white body, muted footer. */
    private static String shell(String title, String preheaderText, String logoUrl, String mainHtml) {
        String logo = notBlank(logoUrl)
                ? "<img src=\"" + escAttr(logoUrl) + "\" alt=\"Trovemo\" height=\"36\" "
                + "style=\"display:block;border:0;height:36px;max-width:220px;\" />"
                : "<span style=\"font-family:" + FONT + ";color:" + SURFACE + ";font-size:24px;"
                + "font-weight:bold;letter-spacing:1px;\">Trovemo</span>";
        return "<!DOCTYPE html>"
                + "<html><head><meta charset=\"utf-8\" />"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />"
                // Opt out of client-forced dark mode: the palette below is a light one.
                + "<meta name=\"color-scheme\" content=\"light\" />"
                + "<meta name=\"supported-color-schemes\" content=\"light\" />"
                + "<title>" + esc(title) + "</title></head>"
                + "<body style=\"margin:0;padding:0;background-color:" + BG + ";"
                + "font-family:" + FONT + ";color-scheme:light;-webkit-text-size-adjust:100%;\">"
                + preheader(preheaderText)
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background-color:" + BG + ";\">"
                + "<tr><td align=\"center\" style=\"padding:24px 12px;\">"
                + "<table role=\"presentation\" width=\"" + WIDTH + "\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:" + WIDTH + "px;width:100%;background-color:" + SURFACE + ";"
                + "border:1px solid " + BORDER + ";border-radius:" + RADIUS_CARD + ";"
                + "border-collapse:separate;overflow:hidden;\">"
                + "<tr><td style=\"background-color:" + PRIMARY_DARK + ";padding:18px 32px;\" align=\"left\">"
                + logo + "</td></tr>"
                + "<tr><td style=\"padding:28px 32px 32px 32px;\">" + mainHtml + "</td></tr>"
                + footer()
                + "</table></td></tr></table></body></html>";
    }

    /**
     * Inbox preview line: hidden in the body, read by the client's list view. The
     * trailing zero-width padding stops Gmail from pulling body copy in after it.
     */
    private static String preheader(String text) {
        if (!notBlank(text)) {
            return "";
        }
        return "<div style=\"display:none;font-family:" + FONT + ";font-size:1px;line-height:1px;max-height:0;max-width:0;"
                + "opacity:0;overflow:hidden;mso-hide:all;color:" + BG + ";\">"
                + esc(text)
                + "&#8203;&#847;&#8203;&#847;&#8203;&#847;&#8203;&#847;&#8203;&#847;</div>";
    }

    private static String footer() {
        return "<tr><td style=\"background-color:" + BG + ";border-top:1px solid " + BORDER + ";padding:18px 32px;\">"
                + footerLine("&mdash; The Trovemo team")
                + footerLine("Questions? Write to <a href=\"mailto:" + SUPPORT_EMAIL + "\" style=\"color:"
                + PRIMARY + ";text-decoration:underline;\">" + SUPPORT_EMAIL + "</a>")
                + "</td></tr>";
    }

    private static String footerLine(String innerHtml) {
        return "<div style=\"font-family:" + FONT + ";color:" + MUTED + ";font-size:12px;line-height:18px;\">"
                + innerHtml + "</div>";
    }

    private static String heading(String text) {
        return "<h1 style=\"margin:0 0 12px 0;font-family:" + FONT + ";color:" + TEXT + ";font-size:22px;"
                + "line-height:30px;font-weight:bold;letter-spacing:-0.01em;\">" + esc(text) + "</h1>";
    }

    /** Body paragraph; {@code innerHtml} is pre-escaped by the caller (may contain markup). */
    private static String paragraph(String innerHtml) {
        return "<p style=\"margin:0 0 16px 0;font-family:" + FONT + ";color:" + TEXT + ";font-size:14px;"
                + "line-height:22px;\">" + innerHtml + "</p>";
    }

    private static String sectionTitle(String text) {
        return "<h2 style=\"margin:20px 0 10px 0;font-family:" + FONT + ";color:" + PRIMARY_DARK + ";"
                + "font-size:15px;line-height:20px;font-weight:bold;text-transform:uppercase;"
                + "letter-spacing:0.5px;\">" + esc(text) + "</h2>";
    }

    /** Vertical rhythm between blocks; a div because Outlook ignores margins on tables. */
    private static String spacer() {
        return "<div style=\"height:20px;line-height:20px;\">&#8203;</div>";
    }

    private static String openTable() {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"border-collapse:collapse;\">";
    }

    /** Boxed block; {@code emphasis} paints the teal tint used for chips on the site. */
    private static String panel(String innerHtml, boolean emphasis) {
        String background = emphasis ? PRIMARY_SOFT : BG;
        String edge = emphasis ? PRIMARY_SOFT_EDGE : BORDER;
        return "<div style=\"background-color:" + background + ";border:1px solid " + edge + ";"
                + "border-radius:" + RADIUS_SM + ";padding:14px 16px;margin:0 0 16px 0;\">" + innerHtml + "</div>";
    }

    private static String panelLine(String innerHtml) {
        return "<div style=\"font-family:" + FONT + ";color:" + TEXT + ";font-size:14px;line-height:22px;\">"
                + innerHtml + "</div>";
    }

    /**
     * The one number (or code) a mail is really about. {@code monospace} is for
     * codes, where character shape matters more than the brand face.
     */
    private static String figure(String label, String value, boolean monospace) {
        String valueFont = monospace ? MONO : FONT;
        String valueStyle = "font-family:" + valueFont + ";color:" + PRIMARY_DARK + ";font-size:28px;"
                + "line-height:36px;font-weight:bold;" + (monospace ? "letter-spacing:3px;" : "");
        return "<div style=\"background-color:" + PRIMARY_SOFT + ";border:1px solid " + PRIMARY_SOFT_EDGE + ";"
                + "border-radius:" + RADIUS_SM + ";padding:16px;margin:0 0 16px 0;\" align=\"center\">"
                + "<div style=\"font-family:" + FONT + ";color:" + MUTED + ";font-size:12px;line-height:18px;"
                + "text-transform:uppercase;letter-spacing:0.5px;\">" + esc(label) + "</div>"
                + "<div style=\"" + valueStyle + "\">" + esc(value) + "</div>"
                + "</div>";
    }

    private static String itemRow(HtmlOrderLine line) {
        String thumb = notBlank(line.imageUrl())
                ? "<img src=\"" + escAttr(line.imageUrl()) + "\" alt=\"\" width=\"56\" height=\"56\" "
                + "style=\"display:block;border:0;width:56px;height:56px;object-fit:cover;"
                + "border-radius:" + RADIUS_SM + ";background-color:" + BORDER + ";\" />"
                : "<div style=\"width:56px;height:56px;border-radius:" + RADIUS_SM + ";"
                + "background-color:" + BORDER + ";\"></div>";
        StringBuilder row = new StringBuilder();
        row.append("<tr>")
                .append("<td width=\"64\" style=\"padding:10px 12px 10px 0;border-bottom:1px solid " + BORDER
                        + ";vertical-align:top;\">")
                .append(thumb).append("</td>")
                .append("<td style=\"padding:10px 8px 10px 0;border-bottom:1px solid " + BORDER
                        + ";vertical-align:top;\">")
                .append("<div style=\"font-family:" + FONT + ";color:" + TEXT + ";font-size:14px;line-height:20px;"
                        + "font-weight:bold;\">")
                .append(esc(line.name())).append("</div>");
        if (notBlank(line.specifications())) {
            row.append("<div style=\"font-family:" + FONT + ";color:" + MUTED + ";font-size:12px;line-height:18px;\">")
                    .append(esc(line.specifications())).append("</div>");
        }
        row.append("<div style=\"font-family:" + FONT + ";color:" + MUTED + ";font-size:12px;line-height:18px;\">Qty: ")
                .append(line.quantity()).append("</div>")
                .append("</td>")
                .append("<td align=\"right\" style=\"padding:10px 0;border-bottom:1px solid " + BORDER + ";"
                        + "vertical-align:top;white-space:nowrap;\">")
                .append("<span style=\"font-family:" + FONT + ";color:" + TEXT + ";font-size:14px;line-height:20px;\">")
                .append(esc(nz(line.price()))).append("</span></td>")
                .append("</tr>");
        return row.toString();
    }

    private static void amountRow(StringBuilder table, String label, String amount, boolean total) {
        amountRow(table, label, amount, total, TEXT);
    }

    /** One label/amount row; a blank amount omits the row, like the plain-text template. */
    private static void amountRow(StringBuilder table, String label, String amount, boolean total, String amountColor) {
        if (!notBlank(amount)) {
            return;
        }
        String labelStyle = total
                ? "padding:10px 8px 2px 0;font-family:" + FONT + ";color:" + TEXT + ";font-size:15px;"
                + "font-weight:bold;border-top:2px solid " + PRIMARY + ";"
                : "padding:3px 8px 3px 0;font-family:" + FONT + ";color:" + MUTED + ";font-size:14px;";
        String amountStyle = total
                ? "padding:10px 0 2px 0;font-family:" + FONT + ";color:" + PRIMARY_DARK + ";font-size:16px;"
                + "font-weight:bold;border-top:2px solid " + PRIMARY + ";white-space:nowrap;"
                : "padding:3px 0;font-family:" + FONT + ";color:" + amountColor + ";font-size:14px;white-space:nowrap;";
        table.append("<tr><td style=\"").append(labelStyle).append("\">").append(esc(label)).append("</td>")
                .append("<td align=\"right\" style=\"").append(amountStyle).append("\">").append(esc(amount))
                .append("</td></tr>");
    }

    /** Primary call to action; a blank URL renders nothing (never a dead button). */
    private static String button(String url, String label) {
        if (!notBlank(url)) {
            return "";
        }
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:8px 0 4px 0;\"><tr>"
                + "<td style=\"background-color:" + PRIMARY + ";border-radius:" + RADIUS_SM + ";\">"
                + "<a href=\"" + escAttr(url) + "\" "
                + "style=\"display:inline-block;padding:11px 24px;font-family:" + FONT + ";color:" + SURFACE + ";"
                + "font-size:14px;font-weight:bold;text-decoration:none;\">" + esc(label) + "</a>"
                + "</td></tr></table>";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Attribute contexts additionally escape the single quote. */
    private static String escAttr(String value) {
        return esc(value).replace("'", "&#39;");
    }
}
