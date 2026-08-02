package org.linlinjava.litemall.core.mail;

import java.util.List;

/**
 * HTML variants (V48) of the customer-mail templates in {@link MailTemplates}.
 * Pure rendering, no Spring: the order enqueue listener renders at enqueue time
 * and stores the result in {@code litemall_mail_outbox.body_html}, next to the
 * plain-text body that stays the multipart fallback (and the admin-panel view).
 *
 * <p>Email-client constraints, deliberately: table layout, inline styles only,
 * absolute image URLs, no external CSS/JS, max-width 600. Colors are the
 * storefront teal theme (#0e7c86 / #0a5d65 / #e3f2f3 / #1f2a2e / #6b7b82).
 *
 * <p>Every dynamic value is HTML-escaped here — callers pass raw strings.
 * Money strings arrive pre-formatted (symbol included), blank = omit the line,
 * exactly like the plain-text templates.
 */
public final class MailHtmlTemplates {

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

    public static String orderConfirmation(OrderConfirmationHtml d) {
        StringBuilder main = new StringBuilder();
        main.append(heading("Thank you for your purchase!"));
        main.append(paragraph("We have received your payment for order <strong>" + esc(d.orderSn()) + "</strong>."
                + (notBlank(d.payTime()) ? " Paid at " + esc(d.payTime()) + "." : "")));

        if (d.lines() != null && !d.lines().isEmpty()) {
            main.append(sectionTitle("Your items"));
            main.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;\">");
            for (HtmlOrderLine line : d.lines()) {
                main.append(itemRow(line));
            }
            main.append("</table>");
        }

        main.append(sectionTitle("Order summary"));
        main.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;\">");
        amountRow(main, "Items subtotal", d.goodsPrice(), false);
        amountRow(main, "Shipping", d.freightPrice(), false);
        amountRow(main, "Coupon discount", d.couponPrice(), false);
        amountRow(main, "Tax", d.taxPrice(), false);
        amountRow(main, "Order total", d.actualPrice(), true);
        main.append("</table>");

        if (d.deliveryLines() != null && !d.deliveryLines().isEmpty()) {
            main.append(sectionTitle("Delivery"));
            main.append("<div style=\"background-color:#f6f9fa;border:1px solid #eceff1;border-radius:6px;padding:14px 16px;\">");
            for (String line : d.deliveryLines()) {
                if (notBlank(line)) {
                    main.append("<div style=\"color:#1f2a2e;font-size:14px;line-height:22px;\">")
                            .append(esc(line)).append("</div>");
                }
            }
            main.append("</div>");
        }

        main.append(paragraph("We will let you know as soon as your order ships."));
        if (notBlank(d.orderUrl())) {
            main.append(button(d.orderUrl(), "View your order"));
        }
        return shell("Order " + nz(d.orderSn()) + " confirmed", d.logoUrl(), main.toString());
    }

    /** Blank {@code trackingNumber} = shipped-without-tracking copy (CJ sometimes lags the number). */
    public static String shipped(String orderSn, String carrier, String trackingNumber, String orderUrl, String logoUrl) {
        StringBuilder main = new StringBuilder();
        main.append(heading("Your order is on its way!"));
        main.append(paragraph("Good news — your order <strong>" + esc(orderSn) + "</strong> has shipped."));
        main.append("<div style=\"background-color:#e3f2f3;border:1px solid #cfe7e9;border-radius:6px;padding:16px;margin:8px 0 4px 0;\">");
        if (notBlank(carrier)) {
            main.append("<div style=\"color:#1f2a2e;font-size:14px;line-height:22px;\">Carrier: <strong>")
                    .append(esc(carrier)).append("</strong></div>");
        }
        if (notBlank(trackingNumber)) {
            main.append("<div style=\"color:#1f2a2e;font-size:14px;line-height:22px;\">Tracking number: "
                            + "<strong style=\"font-family:Consolas,Menlo,monospace;\">")
                    .append(esc(trackingNumber)).append("</strong></div>");
        } else {
            main.append("<div style=\"color:#1f2a2e;font-size:14px;line-height:22px;\">"
                    + "Your tracking number is being assigned — we will send it in a follow-up email.</div>");
        }
        main.append("</div>");
        main.append(paragraph("You can follow the shipment from the order detail page in your account."));
        if (notBlank(orderUrl)) {
            main.append(button(orderUrl, "Track your order"));
        }
        return shell("Order " + nz(orderSn) + " shipped", logoUrl, main.toString());
    }

    // ------------------------------------------------------------------
    // Building blocks
    // ------------------------------------------------------------------

    /** The shared 600px card: teal logo header, white body, muted footer. */
    private static String shell(String title, String logoUrl, String mainHtml) {
        String logo = notBlank(logoUrl)
                ? "<img src=\"" + escAttr(logoUrl) + "\" alt=\"Trovemo\" height=\"36\" "
                + "style=\"display:block;border:0;height:36px;max-width:220px;\" />"
                : "<span style=\"color:#ffffff;font-size:24px;font-weight:bold;letter-spacing:1px;\">Trovemo</span>";
        return "<!DOCTYPE html>"
                + "<html><head><meta charset=\"utf-8\" />"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />"
                + "<title>" + esc(title) + "</title></head>"
                + "<body style=\"margin:0;padding:0;background-color:#f6f9fa;"
                + "font-family:Arial,Helvetica,sans-serif;-webkit-text-size-adjust:100%;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f6f9fa;\">"
                + "<tr><td align=\"center\" style=\"padding:24px 12px;\">"
                + "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:600px;width:100%;background-color:#ffffff;border:1px solid #eceff1;"
                + "border-radius:8px;border-collapse:separate;overflow:hidden;\">"
                + "<tr><td style=\"background-color:#0e7c86;padding:18px 32px;\" align=\"left\">" + logo + "</td></tr>"
                + "<tr><td style=\"padding:28px 32px 32px 32px;\">" + mainHtml + "</td></tr>"
                + "<tr><td style=\"background-color:#f6f9fa;border-top:1px solid #eceff1;padding:18px 32px;\">"
                + "<div style=\"color:#6b7b82;font-size:12px;line-height:18px;\">&mdash; The Trovemo team</div>"
                + "<div style=\"color:#6b7b82;font-size:12px;line-height:18px;\">"
                + "Questions? Write to <a href=\"mailto:support@trovemo.com\" style=\"color:#0e7c86;\">support@trovemo.com</a>"
                + "</div></td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private static String heading(String text) {
        return "<h1 style=\"margin:0 0 12px 0;color:#1f2a2e;font-size:22px;line-height:30px;\">" + esc(text) + "</h1>";
    }

    /** Body paragraph; {@code innerHtml} is pre-escaped by the caller (may contain markup). */
    private static String paragraph(String innerHtml) {
        return "<p style=\"margin:0 0 16px 0;color:#1f2a2e;font-size:14px;line-height:22px;\">" + innerHtml + "</p>";
    }

    private static String sectionTitle(String text) {
        return "<h2 style=\"margin:20px 0 10px 0;color:#0a5d65;font-size:15px;line-height:20px;"
                + "text-transform:uppercase;letter-spacing:0.5px;\">" + esc(text) + "</h2>";
    }

    private static String itemRow(HtmlOrderLine line) {
        String thumb = notBlank(line.imageUrl())
                ? "<img src=\"" + escAttr(line.imageUrl()) + "\" alt=\"\" width=\"56\" height=\"56\" "
                + "style=\"display:block;border:0;width:56px;height:56px;object-fit:cover;"
                + "border-radius:4px;background-color:#eceff1;\" />"
                : "<div style=\"width:56px;height:56px;border-radius:4px;background-color:#eceff1;\"></div>";
        StringBuilder row = new StringBuilder();
        row.append("<tr>")
                .append("<td width=\"64\" style=\"padding:10px 12px 10px 0;border-bottom:1px solid #eceff1;vertical-align:top;\">")
                .append(thumb).append("</td>")
                .append("<td style=\"padding:10px 8px 10px 0;border-bottom:1px solid #eceff1;vertical-align:top;\">")
                .append("<div style=\"color:#1f2a2e;font-size:14px;line-height:20px;font-weight:bold;\">")
                .append(esc(line.name())).append("</div>");
        if (notBlank(line.specifications())) {
            row.append("<div style=\"color:#6b7b82;font-size:12px;line-height:18px;\">")
                    .append(esc(line.specifications())).append("</div>");
        }
        row.append("<div style=\"color:#6b7b82;font-size:12px;line-height:18px;\">Qty: ")
                .append(line.quantity()).append("</div>")
                .append("</td>")
                .append("<td align=\"right\" style=\"padding:10px 0;border-bottom:1px solid #eceff1;"
                        + "vertical-align:top;white-space:nowrap;\">")
                .append("<span style=\"color:#1f2a2e;font-size:14px;line-height:20px;\">")
                .append(esc(nz(line.price()))).append("</span></td>")
                .append("</tr>");
        return row.toString();
    }

    /** One label/amount row; a blank amount omits the row, like the plain-text template. */
    private static void amountRow(StringBuilder table, String label, String amount, boolean total) {
        if (!notBlank(amount)) {
            return;
        }
        String labelStyle = total
                ? "padding:10px 8px 2px 0;color:#1f2a2e;font-size:15px;font-weight:bold;border-top:2px solid #0e7c86;"
                : "padding:3px 8px 3px 0;color:#6b7b82;font-size:14px;";
        String amountStyle = total
                ? "padding:10px 0 2px 0;color:#0a5d65;font-size:16px;font-weight:bold;border-top:2px solid #0e7c86;white-space:nowrap;"
                : "padding:3px 0;color:#1f2a2e;font-size:14px;white-space:nowrap;";
        table.append("<tr><td style=\"").append(labelStyle).append("\">").append(esc(label)).append("</td>")
                .append("<td align=\"right\" style=\"").append(amountStyle).append("\">").append(esc(amount))
                .append("</td></tr>");
    }

    private static String button(String url, String label) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:8px 0 4px 0;\"><tr>"
                + "<td style=\"background-color:#0e7c86;border-radius:6px;\">"
                + "<a href=\"" + escAttr(url) + "\" "
                + "style=\"display:inline-block;padding:11px 24px;color:#ffffff;font-size:14px;font-weight:bold;"
                + "text-decoration:none;\">" + esc(label) + "</a>"
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
