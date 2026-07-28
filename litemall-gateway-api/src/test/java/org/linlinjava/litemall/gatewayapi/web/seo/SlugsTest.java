package org.linlinjava.litemall.gatewayapi.web.seo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Wave-13 slug contract. These cases double as the parity spec for the
 * TypeScript twin ({@code app/shared/util/slug.ts}) — any change here must be
 * mirrored there, or canonical URLs diverge from the hrefs the SPA emits.
 */
class SlugsTest {

    @Test
    @DisplayName("lowercases and dashes word separators")
    void basic() {
        assertThat(Slugs.slug("Wireless Bluetooth Earbuds")).isEqualTo("wireless-bluetooth-earbuds");
    }

    @Test
    @DisplayName("ASCII-folds accented characters")
    void accents() {
        assertThat(Slugs.slug("Crème Brûlée Décor — Père Noël")).isEqualTo("creme-brulee-decor-pere-noel");
    }

    @Test
    @DisplayName("collapses runs of symbols into one dash and trims the ends")
    void symbols() {
        assertThat(Slugs.slug("  50% Off!! (Limited) — Buy 1, Get 1  ")).isEqualTo("50-off-limited-buy-1-get-1");
    }

    @Test
    @DisplayName("caps at 80 chars without a trailing dash")
    void lengthCap() {
        String name = "a".repeat(79) + " bcdef";
        String slug = Slugs.slug(name);
        assertThat(slug).hasSizeLessThanOrEqualTo(80);
        assertThat(slug).doesNotEndWith("-");
        assertThat(slug).startsWith("a".repeat(79));
    }

    @Test
    @DisplayName("fully non-Latin names yield an empty slug, so productPath stays bare")
    void nonLatin() {
        assertThat(Slugs.slug("春季新款连衣裙")).isEmpty();
        assertThat(Slugs.productPath("42", "春季新款连衣裙")).isEqualTo("/product/42");
    }

    @Test
    @DisplayName("null and blank names are safe")
    void nullSafe() {
        assertThat(Slugs.slug(null)).isEmpty();
        assertThat(Slugs.slug("   ")).isEmpty();
        assertThat(Slugs.productPath("7", null)).isEqualTo("/product/7");
    }

    @Test
    @DisplayName("productPath joins id and slug with a dash")
    void productPath() {
        assertThat(Slugs.productPath("10000553", "Vintage Denim Jacket"))
                .isEqualTo("/product/10000553-vintage-denim-jacket");
    }
}
