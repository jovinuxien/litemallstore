package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.core.mail.MailTemplates;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rendering contract of the Wave-10 rich order-confirmation template (lives in
 * litemall-core; tested here where the JUnit-5 surefire setup is proven).
 */
class OrderConfirmationTemplateTest {

    @Test
    void richBody_carriesItemsAmountsAndDelivery() {
        MailTemplates.RenderedMail mail = MailTemplates.orderConfirmation(
                new MailTemplates.OrderConfirmationDetails(
                        "20260726000042",
                        "2026-07-26 14:03",
                        List.of(new MailTemplates.OrderLine("Wireless Mouse", "Black, USB-C", 2, "$9.99"),
                                new MailTemplates.OrderLine("Desk Mat", "", 1, "$19.98")),
                        "$39.96", "$5.00", "-$2.00", "$1.20", "$44.16",
                        "Consignee: Jane Buyer\nPhone: +1 555 0100\nAddress: 1 Main St"));

        assertThat(mail.templateKey()).isEqualTo(MailTemplates.KEY_ORDER_CONFIRMATION);
        assertThat(mail.subject()).isEqualTo("Your Trovemo order 20260726000042 is confirmed");
        assertThat(mail.body())
                .contains("Paid at: 2026-07-26 14:03")
                .contains("Your items")
                .contains("- Wireless Mouse (Black, USB-C) x 2 — $9.99")
                .contains("- Desk Mat x 1 — $19.98")
                .contains("Items subtotal:")
                .contains("Coupon discount: -$2.00")
                .contains("Tax:")
                .containsPattern("Order total:\\s+\\$44\\.16")
                .contains("Delivery")
                .contains("Consignee: Jane Buyer")
                .contains("— The Trovemo team");
        // a blank specification must not leave empty parentheses behind
        assertThat(mail.body()).doesNotContain("Desk Mat ()");
    }

    @Test
    void richBody_omitsBlankSections() {
        MailTemplates.RenderedMail mail = MailTemplates.orderConfirmation(
                new MailTemplates.OrderConfirmationDetails(
                        "20260726000042", "", List.of(),
                        "$39.96", "$5.00", "", "", "$44.16", ""));

        assertThat(mail.body())
                .doesNotContain("Paid at:")
                .doesNotContain("Your items")
                .doesNotContain("Coupon discount:")
                .doesNotContain("Tax:")
                .doesNotContain("Delivery")
                .contains("Items subtotal:")
                .contains("Order total:");
    }

    @Test
    void minimalFallback_isStillTheOrderSnPlusTotalMail() {
        MailTemplates.RenderedMail mail = MailTemplates.orderConfirmation("20260726000042", "$44.16");

        assertThat(mail.templateKey()).isEqualTo(MailTemplates.KEY_ORDER_CONFIRMATION);
        assertThat(mail.subject()).isEqualTo("Your Trovemo order 20260726000042 is confirmed");
        assertThat(mail.body())
                .contains("Order total: $44.16")
                .contains("— The Trovemo team")
                .doesNotContain("Your items");
    }
}
