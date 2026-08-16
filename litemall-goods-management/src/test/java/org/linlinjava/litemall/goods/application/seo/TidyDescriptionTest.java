package org.linlinjava.litemall.goods.application.seo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 26: turning raw CJ supplier listings into store copy.
 *
 * <p>Every input below is a REAL description taken from the live feed on 2026-08-16, where 47% of
 * in-band descriptions carried at least one marketplace artefact. The negative cases matter as
 * much as the positive ones — an over-eager rule silently rewrites 968 product descriptions.
 */
public class TidyDescriptionTest {

    @Test
    public void stripsALeadingSectionLabel() {
        // 30.5% of in-band descriptions opened with a bare form-field label.
        assertEquals("burner caps for gas stove.",
                HtmlText.tidyDescription("Description: burner caps for gas stove."));
        assertEquals("Made by fireproof PC material.",
                HtmlText.tidyDescription("Features: Made by fireproof PC material."));
        assertEquals("Dimensions: Single bed 99 x 190 cm.",
                HtmlText.tidyDescription("Product information: Dimensions: Single bed 99 x 190 cm."));
    }

    /** Only at the START. Mid-text "Features:" legitimately introduces a list. */
    @Test
    public void keepsASectionLabelThatIsActuallyIntroducingSomething() {
        String s = "Portable sewing machine for beginners. Features: two power options, 12 stitches.";
        assertEquals(s, HtmlText.tidyDescription(s));
    }

    @Test
    public void convertsCjkPunctuationUsedInEnglishCopy() {
        assertEquals("Powerful Cleaning Brush: Quick release attachments make cleaning easier.",
                HtmlText.tidyDescription("【Powerful Cleaning Brush】Quick release attachments make cleaning easier."));
        assertEquals("use it anywhere, anytime",
                HtmlText.tidyDescription("use it anywhere、anytime"));
        assertEquals("100% Brand new and high quality!",
                HtmlText.tidyDescription("100% Brand new and high quality！"));
    }

    @Test
    public void turnsMarketplaceBulletLabelsIntoSentences() {
        assertEquals("High-Precision & Heavy-Duty Capacity: Engineered with an advanced sensory system.",
                HtmlText.tidyDescription("[High-Precision & Heavy-Duty Capacity] Engineered with an advanced sensory system."));
    }

    @Test
    public void stripsMarkdownEmphasisThatSurvivedTheSupplierEditor() {
        assertEquals("Kompatibel mit Makita 18-V-Akkus",
                HtmlText.tidyDescription("**Kompatibel mit Makita 18-V-Akkus**"));
    }

    /**
     * German copy comes from the DE-warehoused products — an asset in this market, not an
     * artefact. The tidier must not touch it beyond its normal rules.
     */
    @Test
    public void leavesGermanCopyAlone() {
        String s = "Professionelles, 32-teiliges Werkzeugset aus gehärtetem Chrom-Vanadium-Stahl.";
        assertEquals(s, HtmlText.tidyDescription(s));
    }

    /** Acronyms and units must survive — this is why ALL-CAPS de-shouting was left out. */
    @Test
    public void doesNotMangleAcronymsUnitsOrMeasurements() {
        String s = "USB and LED indicators, 0-10 psi range, 66 lbs. (30 kg) capacity, COVID-19 rated.";
        assertEquals(s, HtmlText.tidyDescription(s));
    }

    @Test
    public void handlesEmptyAndNullWithoutThrowing() {
        assertEquals("", HtmlText.tidyDescription(null));
        assertEquals("", HtmlText.tidyDescription("   "));
    }

    /** The real end-to-end shape: label + CJK bracket + shouty body, as it ships today. */
    @Test
    public void cleansARealCompoundCase() {
        String out = HtmlText.tidyDescription(
                "Description: 【Multifunctional Brush】Comes with 9 interchangeable heads、"
                        + "for kitchen（and bathroom）use。");
        assertFalse(out.contains("Description:"), out);
        assertFalse(out.matches("(?s).*[【】、。（）].*"), "CJK punctuation left: " + out);
        assertTrue(out.startsWith("Multifunctional Brush: Comes with 9"), out);
    }

    /**
     * Real feed row 10000832: the brief held DOUBLE-ENCODED markup, so stripping tags before
     * decoding left the attribute text as the product's description. 233 rows (8% of the feed)
     * shipped `img src="https://oss-cf.cjdropshipping.com/..."` as their description.
     */
    @Test
    public void doubleEncodedMarkupDoesNotBecomeTheDescription() {
        String raw = "&lt;img src=\"https://oss-cf.cjdropshipping.com/product/2026/06/x.jpg\"&gt;";
        String cleaned = HtmlText.clean(raw);
        assertFalse(cleaned.contains("img src"), "leaked markup: " + cleaned);
        assertFalse(cleaned.contains("http"), "leaked URL: " + cleaned);
    }

    @Test
    public void ordinaryProseSurvivesTheExtraStrippingPasses() {
        String s = "Solar lamp for the garden. Waterproof to IP65 & rated 5 < 10 lux.";
        String cleaned = HtmlText.clean(s);
        assertTrue(cleaned.contains("Solar lamp for the garden."), cleaned);
        assertTrue(cleaned.contains("IP65"), cleaned);
    }

    /**
     * THE actual cause of the 233 leaked-markup feed rows. goods.brief is stored truncated at 255
     * characters, which cuts the last tag in half — and a half-tag is not a tag, so the strip
     * misses it and its attributes ship as the product description. Verbatim from goods 10000832.
     */
    @Test
    public void aTagCutInHalfByFieldTruncationIsNotProse() {
        String truncatedAt255 = "<p><img src=\"https://oss-cf.cjdropshipping.com/product/a80cc926.jpg\""
                + " style=\"max-width:100%;\" contenteditable=\"false\"/>"
                + "<img src=\"https://oss-cf.cjdropshipping.com/product/2026/06/22/09/7a153ded-7779-4745-8599-5e2e4";
        String cleaned = HtmlText.clean(truncatedAt255);
        assertFalse(cleaned.contains("img src"), "half-tag leaked as prose: " + cleaned);
        assertFalse(cleaned.contains("oss-cf"), "URL leaked as prose: " + cleaned);
        assertEquals("", cleaned, "an image-only brief must clean to nothing so the fallback runs");
    }

    /** A stray "<" in ordinary prose must not eat the rest of the sentence. */
    @Test
    public void aStrayLessThanDoesNotSwallowTheTail() {
        String cleaned = HtmlText.clean("Rated 5 < 10 lux for garden use");
        assertTrue(cleaned.startsWith("Rated 5"), cleaned);
        assertTrue(cleaned.contains("garden use"), "tail was swallowed: " + cleaned);
    }
}
