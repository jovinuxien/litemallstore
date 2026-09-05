package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.core.mail.MailHtmlTemplates;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rendering contract of the HTML customer-mail templates (they live in
 * litemall-core; tested here where the JUnit-5 surefire setup is proven, next to
 * {@link OrderConfirmationTemplateTest} which covers their plain-text twins).
 *
 * <p>Two kinds of assertion, deliberately mixed: the storefront design tokens a
 * mail must carry to read as the same product as the site, and the email-client
 * rules that are correctness rather than taste (explicit font-family for
 * Outlook, a preheader for the inbox preview line, a light color-scheme so dark
 * mode stops inverting the card).
 */
class MailHtmlTemplateTest {

    /** The storefront's own tokens — if these drift, the mails no longer match the site. */
    private static final String BRAND_BAND = "#0a5d65";   // --lm-primary-dark, the live header bar
    private static final String PRIMARY = "#0e7c86";      // --lm-primary, buttons
    private static final String SUCCESS = "#1f9d6b";      // --lm-success, money saved
    private static final String FONT_HEAD = "'Amazon Ember'";

    private static final String ORDER_URL = "https://trovemo.com/order/42";
    private static final String LOGO_URL = "https://trovemo.com/mail-logo.png";

    private static MailHtmlTemplates.OrderConfirmationHtml confirmation() {
        return new MailHtmlTemplates.OrderConfirmationHtml(
                "20260826000042",
                "2026-08-26 14:03",
                List.of(new MailHtmlTemplates.HtmlOrderLine("Wireless Mouse", "Black, USB-C", 2, "€9.99",
                                "https://trovemo.com/_cdn/cf/mouse.jpg"),
                        new MailHtmlTemplates.HtmlOrderLine("Desk Mat", "", 1, "€19.98", "")),
                "€39.96", "€5.00", "-€2.00", "€1.20", "€44.16",
                List.of("Jane Buyer", "+33 1 23 45 67 89", "1 Rue Principale, Paris"),
                ORDER_URL, LOGO_URL);
    }

    /** All seven customer templates, rendered with realistic input. */
    private static List<String> allTemplates() {
        return List.of(
                MailHtmlTemplates.orderConfirmation(confirmation()),
                MailHtmlTemplates.shipped("20260826000042", "CJPacket", "CJ123456789DE", ORDER_URL, LOGO_URL),
                MailHtmlTemplates.pickupCode("20260826000042", "Trovemo Store, Paris", "482913", ORDER_URL, LOGO_URL),
                MailHtmlTemplates.refundApproved("20260826000042", "€44.16", ORDER_URL, LOGO_URL),
                MailHtmlTemplates.paymentRefunded("20260826000042", "€8.58", "https://trovemo.com", LOGO_URL),
                MailHtmlTemplates.fulfilmentCancelled("20260826000042", ORDER_URL, LOGO_URL),
                MailHtmlTemplates.delivered("20260826000042", "2026-09-05", true, 30, ORDER_URL, LOGO_URL));
    }

    @Test
    void delivered_namesTheDateAndTheReturnWindow_andDistinguishesAutoFromConfirmed() {
        String auto = MailHtmlTemplates.delivered("SN1", "2026-09-05", true, 30, ORDER_URL, LOGO_URL);
        String confirmed = MailHtmlTemplates.delivered("SN1", "2026-09-05", false, 30, ORDER_URL, LOGO_URL);

        assertThat(auto).contains("2026-09-05").contains("30 days").contains("did not hear otherwise");
        assertThat(confirmed).contains("Thanks for confirming").doesNotContain("did not hear otherwise");
    }

    @Test
    void fulfilmentCancelled_promisesContactNotARefundDate_andNeverSaysShipped() {
        String html = MailHtmlTemplates.fulfilmentCancelled("SN1", ORDER_URL, LOGO_URL);

        assertThat(html).contains("could not fulfil").contains("will contact you").contains("do not need to do anything");
        assertThat(html).doesNotContain("has shipped").doesNotContain("refund has been issued");
    }

    @Test
    void paymentRefunded_saysNothingShips_andSendsTheCustomerBackToTheStore() {
        String html = MailHtmlTemplates.paymentRefunded("SN1", "€8.58", "https://trovemo.com", LOGO_URL);

        assertThat(html).contains("€8.58").contains("Nothing will be shipped").contains("place a new order");
        assertThat(html).contains("href=\"https://trovemo.com\"").contains("Shop again");
        assertThat(html).doesNotContain("View your order"); // there is no order to view
    }

    // ------------------------------------------------------------------
    // Content
    // ------------------------------------------------------------------

    @Test
    void orderConfirmation_carriesItemsAmountsDeliveryAndCta() {
        String html = MailHtmlTemplates.orderConfirmation(confirmation());

        assertThat(html)
                .contains("Thank you for your purchase!")
                .contains("20260826000042")
                .contains("Paid at 2026-08-26 14:03")
                .contains("Wireless Mouse")
                .contains("Black, USB-C")
                .contains("Qty: 2")
                .contains("€9.99")
                .contains("Items subtotal")
                .contains("Order total")
                .contains("€44.16")
                .contains("1 Rue Principale, Paris")
                .contains("View your order")
                .contains(ORDER_URL);
    }

    @Test
    void orderConfirmation_paintsDiscountAsSavingAndTotalAsBrand() {
        String html = MailHtmlTemplates.orderConfirmation(confirmation());

        // The coupon line is money saved — the storefront's success green, not body grey.
        assertThat(rowStyleOf(html, "-€2.00")).contains(SUCCESS);
        // The grand total is the brand figure.
        assertThat(rowStyleOf(html, "€44.16")).contains(BRAND_BAND);
    }

    @Test
    void orderConfirmation_blankAmountsOmitTheirRowsEntirely() {
        MailHtmlTemplates.OrderConfirmationHtml d = new MailHtmlTemplates.OrderConfirmationHtml(
                "20260826000043", "", List.of(), "€10.00", "", "", "", "€10.00", List.of(), "", "");
        String html = MailHtmlTemplates.orderConfirmation(d);

        assertThat(html).doesNotContain("Coupon discount").doesNotContain("Tax").doesNotContain("Shipping");
        assertThat(html).contains("Items subtotal").contains("Order total");
    }

    @Test
    void shipped_withoutTrackingNumber_promisesTheFollowUpInsteadOfAnEmptyLine() {
        String withNumber = MailHtmlTemplates.shipped("SN1", "CJPacket", "CJ999", ORDER_URL, LOGO_URL);
        String without = MailHtmlTemplates.shipped("SN1", "CJPacket", "", ORDER_URL, LOGO_URL);

        assertThat(withNumber).contains("Tracking number:").contains("CJ999");
        assertThat(without).doesNotContain("Tracking number:")
                .contains("being assigned");
    }

    @Test
    void pickupCode_presentsTheCodeAsTheFigureOfTheMail() {
        String html = MailHtmlTemplates.pickupCode("SN1", "Trovemo Store, Paris", "482913", ORDER_URL, LOGO_URL);

        assertThat(html)
                .contains("Pickup code")
                .contains("482913")
                .contains("Trovemo Store, Paris")
                .contains("Show this code at the counter");
        // Codes are read character by character: monospace + tracking, not the brand face.
        assertThat(figureValueStyleOf(html, "482913")).contains("monospace").contains("letter-spacing:3px");
    }

    @Test
    void pickupCode_withoutCode_rendersNoEmptyFigureBox() {
        String html = MailHtmlTemplates.pickupCode("SN1", "Trovemo Store, Paris", "", ORDER_URL, LOGO_URL);

        assertThat(html).doesNotContain("Show this code at the counter")
                .contains("being issued");
    }

    @Test
    void refundApproved_showsTheAmountAndDegradesWhenItIsUnknown() {
        String withAmount = MailHtmlTemplates.refundApproved("SN1", "€44.16", ORDER_URL, LOGO_URL);
        String without = MailHtmlTemplates.refundApproved("SN1", "", ORDER_URL, LOGO_URL);

        assertThat(withAmount).contains("Refund amount").contains("€44.16");
        // No figure at all rather than an empty one; the copy still states the refund.
        assertThat(without).doesNotContain("Refund amount")
                .contains("original payment method");
    }

    // ------------------------------------------------------------------
    // Shared shell: brand + email-client rules
    // ------------------------------------------------------------------

    @Test
    void everyTemplate_wearsTheStorefrontShell() {
        for (String html : allTemplates()) {
            assertThat(html).startsWith("<!DOCTYPE html>");
            assertThat(html).contains("background-color:" + BRAND_BAND); // the site's header bar
            assertThat(html).contains(FONT_HEAD);                        // the site's face stack
            assertThat(html).contains("border-radius:10px");             // --lm-radius card
            assertThat(html).contains("support@trovemo.com");
            assertThat(html).contains("The Trovemo team");
            assertThat(html).contains("background-color:" + PRIMARY);    // primary CTA
        }
    }

    @Test
    void everyTemplate_opensWithAPreheaderAndOptsOutOfForcedDarkMode() {
        for (String html : allTemplates()) {
            assertThat(html).contains("<meta name=\"color-scheme\" content=\"light\" />");
            assertThat(html).contains("supported-color-schemes");
            // Hidden preview line, before any visible copy.
            int preheader = html.indexOf("display:none;font-family:");
            int card = html.indexOf("<table role=\"presentation\" width=\"100%\"");
            assertThat(preheader).isGreaterThan(0);
            assertThat(preheader).isLessThan(card);
        }
    }

    @Test
    void everySizedNode_namesItsFontFamily_soOutlookNeverFallsBackToTimes() {
        for (String html : allTemplates()) {
            List<String> offenders = new ArrayList<>();
            Matcher m = Pattern.compile("style=\"([^\"]*)\"").matcher(html);
            while (m.find()) {
                String style = m.group(1);
                if (style.contains("font-size:") && !style.contains("font-family:")) {
                    offenders.add(style);
                }
            }
            assertThat(offenders).as("styles sizing text without naming a face").isEmpty();
        }
    }

    @Test
    void blankUrls_renderNoDeadButtonAndNoBrokenLogo() {
        String html = MailHtmlTemplates.refundApproved("SN1", "€1.00", "", "");

        assertThat(html).doesNotContain("<a href=\"\"").doesNotContain("View your order");
        assertThat(html).doesNotContain("<img");
        assertThat(html).contains(">Trovemo<"); // wordmark fallback
    }

    @Test
    void hostileContentIsEscapedEverywhereItIsInterpolated() {
        MailHtmlTemplates.OrderConfirmationHtml d = new MailHtmlTemplates.OrderConfirmationHtml(
                "<script>alert(1)</script>",
                "",
                List.of(new MailHtmlTemplates.HtmlOrderLine("Mouse \"XL\" <b>", "<i>Black</i>", 1, "€1.00",
                        "https://x/y.jpg?a=1&b='2'")),
                "€1.00", "", "", "", "€1.00",
                List.of("<img src=x onerror=alert(1)>"),
                "https://trovemo.com/order/1?a=1&b='2'", "");
        String html = MailHtmlTemplates.orderConfirmation(d);

        // No injected value may re-open as markup; the payload survives only as inert text
        // (so assert on the TAG, not on a substring like "onerror=" that escaping leaves alone).
        assertThat(html).doesNotContain("<script>").doesNotContain("<b>").doesNotContain("<i>")
                .doesNotContain("<img src=x");
        assertThat(html).contains("&lt;script&gt;")
                .contains("&lt;img src=x onerror=alert(1)&gt;")
                .contains("Mouse &quot;XL&quot;")
                .contains("&amp;b=&#39;2&#39;");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** The inline style of the table cell rendering {@code amount}. */
    private static String rowStyleOf(String html, String amount) {
        Matcher m = Pattern.compile("style=\"([^\"]*)\"[^>]*>" + Pattern.quote(amount) + "</td>").matcher(html);
        assertThat(m.find()).as("amount cell for %s", amount).isTrue();
        return m.group(1);
    }

    /** The inline style of the emphasised figure rendering {@code value}. */
    private static String figureValueStyleOf(String html, String value) {
        Matcher m = Pattern.compile("style=\"([^\"]*)\">" + Pattern.quote(value) + "</div>").matcher(html);
        assertThat(m.find()).as("figure for %s", value).isTrue();
        return m.group(1);
    }
}
