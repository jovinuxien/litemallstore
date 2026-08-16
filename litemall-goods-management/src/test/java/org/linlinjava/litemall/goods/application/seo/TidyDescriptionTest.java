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
}
