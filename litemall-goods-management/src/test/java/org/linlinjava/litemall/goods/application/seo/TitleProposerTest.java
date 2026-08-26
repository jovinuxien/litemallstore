package org.linlinjava.litemall.goods.application.seo;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.goods.application.seo.KeywordResearchProvider.KeywordDemand;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TitleProposerTest {

    private static KeywordDemand term(String t, Integer volume) {
        return new KeywordDemand(t, volume, null, null);
    }

    /** A real over-length title from the live catalogue (93 chars). */
    private static final String REAL_LONG_TITLE =
            "Rechargeable Device For Smoothing Calluses And Dead Skin Leaving Your Feet Beautifully Smooth";

    @Test
    public void truncatesOnAWordBoundaryNotMidWord() {
        TitleProposer.Proposal p = TitleProposer.propose(REAL_LONG_TITLE, List.of(), 60);

        assertThat(p.overLength()).isTrue();
        assertThat(p.proposedLength()).isLessThanOrEqualTo(60);
        assertThat(p.proposedTitle()).isEqualTo("Rechargeable Device For Smoothing Calluses And Dead Skin");
        assertThat(REAL_LONG_TITLE).startsWith(p.proposedTitle());
    }

    @Test
    public void leavesAShortTitleAloneAndFlagsItAsNotOverLength() {
        TitleProposer.Proposal p = TitleProposer.propose("Oak Dining Chair", List.of(), 60);

        assertThat(p.overLength()).isFalse();
        assertThat(p.proposedTitle()).isEqualTo("Oak Dining Chair");
    }

    @Test
    public void reportsTheHighestDemandTermTheTitleActuallyContains() {
        TitleProposer.Proposal p = TitleProposer.propose(
                "Outdoor Wall Lamps Waterproof Solar Powered Garden Light For Patio And Driveway",
                List.of(term("outdoor wall lamps", 27100), term("solar powered", 5400)), 60);

        assertThat(p.matchedKeyword()).isEqualTo("outdoor wall lamps");
        assertThat(p.matchedKeywordVolume()).isEqualTo(27100);
    }

    @Test
    public void doesNotClaimATermTheTitleOnlyContainsAsASubstring() {
        // "lamps" is inside "lampshade" — a substring test would claim demand this title
        // has not earned.
        TitleProposer.Proposal p = TitleProposer.propose(
                "Decorative Lampshade Fabric Cover Replacement For Table Lighting Fixtures Home",
                List.of(term("wall lamps", 27100)), 60);

        assertThat(p.matchedKeyword()).isNull();
    }

    @Test
    public void flagsForReviewWhenTruncationCutsTheMatchedKeywordOut() {
        // The term is real and present, but it sits past the 60-character mark, so the
        // shortened title would no longer carry it.
        TitleProposer.Proposal p = TitleProposer.propose(
                "Waterproof Solar Powered Decorative Illumination Fixture For Outdoor Wall Lamps",
                List.of(term("outdoor wall lamps", 27100)), 60);

        assertThat(p.matchedKeyword()).isEqualTo("outdoor wall lamps");
        assertThat(p.keywordSurvives()).isFalse();
        assertThat(p.needsReview()).isTrue();
    }

    @Test
    public void doesNotFlagForReviewWhenTheKeywordSurvivesTheCut() {
        TitleProposer.Proposal p = TitleProposer.propose(
                "Outdoor Wall Lamps Waterproof Solar Powered Garden Light For Patio And Driveway",
                List.of(term("outdoor wall lamps", 27100)), 60);

        assertThat(p.keywordSurvives()).isTrue();
        assertThat(p.needsReview()).isFalse();
    }

    @Test
    public void keepsTheOriginalWhenTruncationWouldDestroyTheMeaning() {
        // First word alone blows the budget: any cut leaves nothing a shopper could act on.
        String title = "Antidisestablishmentarianism Commemorative Porcelain Collectors Plate";
        TitleProposer.Proposal p = TitleProposer.propose(title, List.of(), 20);

        assertThat(p.proposedTitle()).isEqualTo(title);
        assertThat(p.needsReview()).isTrue();
    }

    @Test
    public void stripsMarkupAndCollapsesWhitespaceBeforeMeasuring() {
        TitleProposer.Proposal p = TitleProposer.propose(
                "<b>Garden   Tools</b>  Set", List.of(), 60);

        assertThat(p.currentTitle()).isEqualTo("Garden Tools Set");
        assertThat(p.currentLength()).isEqualTo(16);
    }

    @Test
    public void ignoresStopWordsWhenDecidingATermIsPresent() {
        // "for the garden" vs a title saying "garden" — the filler words must not be what
        // makes or breaks the match.
        TitleProposer.Proposal p = TitleProposer.propose(
                "Heavy Duty Garden Tools Stainless Steel Hand Trowel", List.of(),  60);
        assertThat(p.overLength()).isFalse();

        TitleProposer.Proposal q = TitleProposer.propose(
                "Heavy Duty Garden Tools Stainless Steel Hand Trowel Rake And Fork Set For Beds",
                List.of(term("tools for the garden", 22200)), 60);
        assertThat(q.matchedKeyword()).isEqualTo("tools for the garden");
    }

    @Test
    public void blankOrMarkupOnlyTitleYieldsNoProposal() {
        assertThat(TitleProposer.propose("   ", List.of(), 60)).isNull();
        assertThat(TitleProposer.propose("<br/>", List.of(), 60)).isNull();
    }
}
