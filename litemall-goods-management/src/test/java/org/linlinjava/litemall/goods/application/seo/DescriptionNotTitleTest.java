package org.linlinjava.litemall.goods.application.seo;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallGoods;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 26 Phase 3. The description policy must compare LIKE WITH LIKE: the emitted title comes
 * from {@code clean(name)}, so the "does the brief merely repeat the name?" test has to clean the
 * name too. It did not, and a raw name carrying double spaces or entity soup slipped through —
 * the brief was returned, the feed cleaned both sides, and Merchant Center received 45
 * description==title rows from a method that believed it had prevented exactly that.
 */
public class DescriptionNotTitleTest {

    private static final int MAX = 5000;

    private static LitemallGoods goods(String name, String brief, String detail) {
        LitemallGoods g = new LitemallGoods();
        g.setName(name);
        g.setBrief(brief);
        g.setDetail(detail);
        return g;
    }

    /** The exact production row that exposed this (goods 10000879): name has doubled spaces. */
    @Test
    public void aBriefRepeatingAWhitespaceNoisyNameDoesNotBecomeTheDescription() {
        LitemallGoods g = goods(
                "Double Wall Plug Socket 2 Gang 13A W  2 Charger USB  Outlets Flat Plate UK",
                "Double Wall Plug Socket 2 Gang 13A W  2 Charger USB  Outlets Flat Plate UK",
                "<p>Features: Made by fireproof PC material, high temperature resistant.</p>");

        String description = HtmlText.clean(GoodsMetaService.descriptionOf(g, MAX));

        assertThat(description).doesNotStartWith("Double Wall Plug Socket");
        assertThat(description).contains("fireproof PC material");
    }

    @Test
    public void entitySoupInTheNameIsNormalisedBeforeComparing() {
        LitemallGoods g = goods(
                "Kettle &amp; Jug &nbsp; Set",
                "Kettle &amp; Jug &nbsp; Set",
                "<p>Holds 1.7 litres and keeps water hot for two hours.</p>");

        String description = HtmlText.clean(GoodsMetaService.descriptionOf(g, MAX));

        assertThat(description).contains("1.7 litres");
    }

    @Test
    public void caseOnlyDifferencesStillCountAsARepeat() {
        LitemallGoods g = goods("GARDEN HOSE REEL 20M", "garden hose reel 20m",
                "<p>Wall mounted, holds 20 metres of hose.</p>");

        String description = HtmlText.clean(GoodsMetaService.descriptionOf(g, MAX));

        assertThat(description).contains("Wall mounted");
    }

    /** A genuinely distinct brief is still preferred over the detail body. */
    @Test
    public void aRealBriefIsStillUsed() {
        LitemallGoods g = goods("Garden Hose Reel 20m",
                "Wall-mounted reel with a rewind crank and brass fittings.",
                "<p>Long detail body.</p>");

        assertThat(GoodsMetaService.descriptionOf(g, MAX))
                .isEqualTo("Wall-mounted reel with a rewind crank and brass fittings.");
    }

    /**
     * Image-only detail markup cleans to nothing, so there is no prose to fall back to. The brief
     * stands — honest, and the only remaining duplicates are goods with no source text at all
     * (they need CJ detail enrichment, not a better fallback).
     */
    @Test
    public void imageOnlyDetailLeavesTheBriefStanding() {
        LitemallGoods g = goods("Solar Lamp", "Solar Lamp",
                "<p><img src=\"https://cf.cjdropshipping.com/x.jpg\"></p>");

        assertThat(HtmlText.clean(GoodsMetaService.descriptionOf(g, MAX))).isEqualTo("Solar Lamp");
    }

    @Test
    public void detailThatOnlyRepeatsTheNameIsNotUsedEither() {
        LitemallGoods g = goods("Solar Lamp", "Solar Lamp", "<p>Solar   Lamp</p>");

        assertThat(HtmlText.clean(GoodsMetaService.descriptionOf(g, MAX))).isEqualTo("Solar Lamp");
    }

    @Test
    public void nullNameIsNotAnException() {
        LitemallGoods g = goods(null, "A real brief.", "<p>detail</p>");

        assertThat(GoodsMetaService.descriptionOf(g, MAX)).isEqualTo("A real brief.");
    }
}
