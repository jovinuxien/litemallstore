package org.linlinjava.litemall.promotion.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The promotion twin of the Wave-13 contract slug (goods-management carries the
 * original, gateway-api a TS copy) — these cases mirror the original's suite so
 * drift between the twins shows up as a test diff, not a broken canonical link.
 */
class SeoSluggerTest {

    @Test
    void lowercasesAndDashesSeparators() {
        assertEquals("wireless-earbuds-pro-2", SeoSlugger.slug("Wireless Earbuds  Pro 2"));
    }

    @Test
    void foldsAccentsToAscii() {
        assertEquals("cafe-creme-a-la-francaise", SeoSlugger.slug("Café Crème à la Française"));
    }

    @Test
    void dropsNonLatinAndCollapsesRuns() {
        // CJK drops entirely; the surviving latin part keeps a single dash per run.
        assertEquals("t-shirt", SeoSlugger.slug("酷炫 T-Shirt 夏季"));
    }

    @Test
    void symbolsCollapseAndTrim() {
        assertEquals("50-off-deal", SeoSlugger.slug("**50% OFF!! (deal)**"));
    }

    @Test
    void emptyForAllSymbolNames() {
        assertEquals("", SeoSlugger.slug("!!!***"));
        assertEquals("", SeoSlugger.slug(null));
    }

    @Test
    void capsAtEightyCharsWithoutTrailingDash() {
        String longName = "a".repeat(79) + " bcd";
        String slug = SeoSlugger.slug(longName);
        assertEquals(79, slug.length()); // 79 a's; the dash at position 80 is trimmed
        assertEquals("a".repeat(79), slug);
    }

    @Test
    void productPathUsesSlugOrDegradesToBareId() {
        assertEquals("/product/42-blue-mug", SeoSlugger.productPath(42, "Blue Mug"));
        assertEquals("/product/42", SeoSlugger.productPath(42, "只有中文"));
    }
}
