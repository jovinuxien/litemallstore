package org.linlinjava.litemall.goods.application.seo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SeoSluggerTest {

    @Test
    void lowercasesAndDashesWords() {
        assertThat(SeoSlugger.slug("Wireless Bluetooth Earbuds")).isEqualTo("wireless-bluetooth-earbuds");
    }

    @Test
    void keepsDigits() {
        assertThat(SeoSlugger.slug("iPhone 15 Pro Max")).isEqualTo("iphone-15-pro-max");
    }

    @Test
    void foldsAccentsToAscii() {
        assertThat(SeoSlugger.slug("Café Crème Brûlée à Niño")).isEqualTo("cafe-creme-brulee-a-nino");
    }

    @Test
    void collapsesSymbolRunsToSingleDash() {
        assertThat(SeoSlugger.slug("A™ & B — 100% cotton!!!")).isEqualTo("a-b-100-cotton");
    }

    @Test
    void trimsLeadingAndTrailingSeparators() {
        assertThat(SeoSlugger.slug("  --Hello World--  ")).isEqualTo("hello-world");
    }

    @Test
    void nonLatinOnlyNameYieldsEmptySlug() {
        // CJK does not ASCII-fold; the contract's answer is the bare-id URL.
        assertThat(SeoSlugger.slug("蓝牙耳机")).isEmpty();
        assertThat(SeoSlugger.slug("★☆★")).isEmpty();
    }

    @Test
    void nullAndEmptyYieldEmptySlug() {
        assertThat(SeoSlugger.slug(null)).isEmpty();
        assertThat(SeoSlugger.slug("")).isEmpty();
    }

    @Test
    void capsAtEightyCharsWithoutTrailingDash() {
        String name = "a".repeat(79) + " bc";
        String slug = SeoSlugger.slug(name);
        // Raw slug is 79 a's + "-bc" (82 chars); the cut at 80 would end on the dash — trimmed.
        assertThat(slug).isEqualTo("a".repeat(79));
        assertThat(SeoSlugger.slug("x".repeat(200))).hasSize(80);
    }

    @Test
    void isDeterministic() {
        String name = "Ergonomic Café Chair — 2026 Edition";
        assertThat(SeoSlugger.slug(name)).isEqualTo(SeoSlugger.slug(name));
    }

    @Test
    void productPathAppendsSlugOnlyWhenPresent() {
        assertThat(SeoSlugger.productPath(10000553, "Wireless Earbuds")).isEqualTo("/product/10000553-wireless-earbuds");
        assertThat(SeoSlugger.productPath(10000553, "蓝牙耳机")).isEqualTo("/product/10000553");
        assertThat(SeoSlugger.productPath(10000553, null)).isEqualTo("/product/10000553");
    }
}
