package org.linlinjava.litemall.goods.application.season;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The title check that keeps a generous search index from curating the season rail. */
public class SeasonTermsTest {

    @Test
    public void aLeadingDashTurnsATermIntoAnExclusion() {
        SeasonTerms terms = SeasonTerms.of(Arrays.asList("blanket", "-summer", " -cooling ", "-", "", null));

        assertEquals(List.of("blanket"), terms.discoveryTerms());
        assertEquals(List.of("summer", "cooling"), terms.exclusionTerms());
    }

    /** The live miss: the title contains the term, and also the season it is NOT for. */
    @Test
    public void anExclusionTermInTheTitleVetoesTheHit() {
        SeasonTerms terms = SeasonTerms.of(List.of("blanket", "-summer"));

        assertTrue(terms.excludes("Cartoon-Printed Summer Cooling Air-Conditioning Blanket"));
        assertFalse(terms.excludes("Chunky Knit Autumn Blanket"));
        assertFalse(terms.excludes(null));
    }

    @Test
    public void theTermMustAppearAsAWholeWordCaseInsensitively() {
        assertTrue(SeasonTerms.containsTerm("Chunky Knit BLANKET", "blanket"));
        assertTrue(SeasonTerms.containsTerm("Autumn Leaves Rug, Washable", "rug"));
        assertFalse(SeasonTerms.containsTerm("Drug Storage Box", "rug"),
                "a term inside another word is not a match");
        assertFalse(SeasonTerms.containsTerm("Indoor Waterfall Fountain", "fall"),
                "waterfall is not fall");
    }

    @Test
    public void pluralsAndPunctuationBoundariesAreTolerated() {
        assertTrue(SeasonTerms.containsTerm("Set of 4 Wool Blankets", "blanket"));
        assertTrue(SeasonTerms.containsTerm("Scented Candles (Pack of 6)", "candle"));
        assertTrue(SeasonTerms.containsTerm("Pumpkin-Shaped Candle Holder", "pumpkin"));
        assertTrue(SeasonTerms.containsTerm("Anti-Fall Bath Mat", "anti-fall"),
                "a hyphenated exclusion term matches the hyphenated title");
    }

    @Test
    public void multiWordTermsMatchAcrossWhitespace() {
        assertTrue(SeasonTerms.containsTerm("LED Fairy  Lights 10m", "fairy lights"));
        assertFalse(SeasonTerms.containsTerm("Fairy Garden Lights", "fairy lights"),
                "the words must be adjacent");
    }

    /** Fail closed: a hit whose title the search did not return cannot be verified. */
    @Test
    public void aMissingTitleNeverMatches() {
        assertFalse(SeasonTerms.containsTerm(null, "blanket"));
        assertFalse(SeasonTerms.containsTerm("  ", "blanket"));
        assertFalse(SeasonTerms.containsTerm("Blanket", null));
    }
}
