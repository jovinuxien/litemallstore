package org.linlinjava.litemall.goods.application.season;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallSeasonCandidateMapper;
import org.linlinjava.litemall.db.dao.LitemallSeasonRuleMapper;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.db.domain.LitemallSeasonCandidate;
import org.linlinjava.litemall.db.domain.LitemallSeasonRule;
import org.linlinjava.litemall.db.service.LitemallGoodsProductService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.application.search.SearchService;
import org.linlinjava.litemall.goods.domain.service.elastic.EuStockSignalResolver;
import org.linlinjava.litemall.goods.domain.service.elastic.SeasonSignalResolver;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSeasonProperties;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The scoring pass: what gets published, what gets held, and what a veto does. */
public class SeasonScoringServiceTest {

    private LitemallSeasonRuleMapper ruleMapper;
    private LitemallSeasonCandidateMapper candidateMapper;
    private LitemallGoodsService goodsService;
    private LitemallGoodsProductService productService;
    private SearchService searchService;
    private SeasonSignalResolver signalResolver;
    private EuStockSignalResolver euResolver;
    private LitemallSeasonProperties properties;
    private SeasonScoringService service;

    private static final LocalDate DAY = LocalDate.of(2026, 9, 15);

    @BeforeEach
    public void setUp() {
        ruleMapper = mock(LitemallSeasonRuleMapper.class);
        candidateMapper = mock(LitemallSeasonCandidateMapper.class);
        goodsService = mock(LitemallGoodsService.class);
        productService = mock(LitemallGoodsProductService.class);
        searchService = mock(SearchService.class);
        signalResolver = mock(SeasonSignalResolver.class);
        euResolver = mock(EuStockSignalResolver.class);
        properties = new LitemallSeasonProperties();

        LitemallGoodsProperties goodsProperties = new LitemallGoodsProperties();
        goodsProperties.setPriceFloor(new BigDecimal("5.00"));

        when(euResolver.euFlag(any())).thenReturn(0);
        when(candidateMapper.countDismissed(anyString(), anyInt())).thenReturn(0);

        service = new SeasonScoringService(ruleMapper, candidateMapper, goodsService,
                productService, searchService, signalResolver, euResolver, properties,
                goodsProperties, new ObjectMapper());
    }

    private LitemallSeasonRule autumn() {
        LitemallSeasonRule rule = new LitemallSeasonRule();
        rule.setSeasonKey("autumn");
        rule.setName("Autumn");
        rule.setTerms("[\"blanket\"]");
        rule.setCategoryIds("[]");
        rule.setWeights("{}");
        rule.setEnabled(true);
        return rule;
    }

    /** A search hit as {@code toGoodsListItem} shapes it: STRING id (see goodsId()) + title. */
    private static Map<String, Object> hitRow(int goodsId, String title) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", String.valueOf(goodsId));
        row.put("name", title);
        return row;
    }

    private static Map<String, Object> searchResult(boolean relaxed, Map<String, Object>... rows) {
        Map<String, Object> result = new HashMap<>();
        result.put("goodsList", List.of(rows));
        result.put("total", rows.length);
        result.put("relaxed", relaxed);
        result.put("queryStrategy", relaxed ? "relaxed-ngram-query" : "default-query");
        return result;
    }

    /** A real product behind a hit: profitable, in stock, freshly arrived. */
    private LitemallGoods givenGoods(int goodsId, String name, String retail, String cost, int stock) {
        LitemallGoods goods = new LitemallGoods();
        goods.setId(goodsId);
        goods.setName(name);
        goods.setRetailPrice(new BigDecimal(retail));
        goods.setCost(new BigDecimal(cost));
        goods.setIsOnSale(true);
        goods.setDeleted(false);
        goods.setRating(new BigDecimal("4.6"));
        goods.setReviewCount(30);
        goods.setAddTime(LocalDateTime.now().minusDays(3));
        when(goodsService.findById(goodsId)).thenReturn(goods);

        LitemallGoodsProduct product = new LitemallGoodsProduct();
        product.setNumber(stock);
        when(productService.queryByGid(goodsId)).thenReturn(List.of(product));
        return goods;
    }

    /** A strong, profitable, in-stock product — the only candidate, so it is hot by quantile. */
    private void givenOneStrongHit(int goodsId) {
        when(searchService.search(eq("blanket"), anyInt(), anyInt(), any(), any()))
                .thenReturn(searchResult(false, hitRow(goodsId, "Chunky Knit Blanket")));

        LitemallGoods goods = new LitemallGoods();
        goods.setId(goodsId);
        goods.setName("Chunky Knit Blanket");
        goods.setRetailPrice(new BigDecimal("40.00"));
        goods.setCost(new BigDecimal("16.00"));
        goods.setIsOnSale(true);
        goods.setDeleted(false);
        goods.setRating(new BigDecimal("4.6"));
        goods.setReviewCount(30);
        goods.setAddTime(LocalDateTime.now().minusDays(3));
        when(goodsService.findById(goodsId)).thenReturn(goods);

        LitemallGoodsProduct product = new LitemallGoodsProduct();
        product.setNumber(50);
        when(productService.queryByGid(goodsId)).thenReturn(List.of(product));
    }

    private LitemallSeasonCandidate captureUpsert() {
        ArgumentCaptor<LitemallSeasonCandidate> captor =
                ArgumentCaptor.forClass(LitemallSeasonCandidate.class);
        verify(candidateMapper).upsertProposal(captor.capture());
        return captor.getValue();
    }

    @Test
    public void aStrongCandidateIsPublishedAutomatically() {
        givenOneStrongHit(101);

        SeasonScoringService.SeasonRunResult result = service.scoreSeason(autumn(), DAY);

        LitemallSeasonCandidate row = captureUpsert();
        assertEquals(LitemallSeasonCandidate.STATUS_AUTO, row.getStatus());
        assertEquals("autumn", row.getSeasonKey());
        assertEquals(DAY, row.getDay());
        assertEquals(1, result.published());
    }

    /**
     * The load-bearing one. The upsert guard only protects the row for the SAME day, so without an
     * explicit carry-forward a "permanent" veto would quietly expire the next time the scorer ran
     * and the product would reappear on the page.
     */
    @Test
    public void aVetoIsCarriedForwardOntoTheNextDaysRow() {
        givenOneStrongHit(101);
        when(candidateMapper.countDismissed("autumn", 101)).thenReturn(1);

        service.scoreSeason(autumn(), DAY);

        assertEquals(LitemallSeasonCandidate.STATUS_DISMISSED, captureUpsert().getStatus(),
                "a veto must outlive the day it was cast on");
    }

    @Test
    public void autoPublishDisabledHoldsEverythingAtProposed() {
        properties.setAutoPublishEnabled(false);
        givenOneStrongHit(101);

        SeasonScoringService.SeasonRunResult result = service.scoreSeason(autumn(), DAY);

        assertEquals(LitemallSeasonCandidate.STATUS_PROPOSED, captureUpsert().getStatus());
        assertEquals(0, result.published(), "nothing reaches a page, however well it scored");
    }

    /** An uncosted product is never written at all — it is rejected before scoring. */
    @Test
    public void anUncostedProductIsRejectedAndCounted() {
        givenOneStrongHit(101);
        LitemallGoods uncosted = goodsService.findById(101);
        uncosted.setCost(null);

        SeasonScoringService.SeasonRunResult result = service.scoreSeason(autumn(), DAY);

        verify(candidateMapper, never()).upsertProposal(any());
        assertEquals(0, result.scored());
        assertEquals(1, result.rejections()
                .get(SeasonCandidateScorer.Rejection.UNCOSTED.name()));
    }

    /** The audit trail: the hash groups rows, the snapshot is what survives a rule edit. */
    @Test
    public void everyRowCarriesTheConfigHashAndTheWeightsSnapshot() {
        givenOneStrongHit(101);

        service.scoreSeason(autumn(), DAY);

        LitemallSeasonCandidate row = captureUpsert();
        assertNotNull(row.getConfigVersionHash());
        assertTrue(row.getConfigSnapshot().contains("euMultiplier"),
                "the effective weights travel with the row, not just a fingerprint of them");
    }

    /** A season whose scoring throws must not take the other seasons down with it. */
    @Test
    public void oneFailingSeasonDoesNotStopTheRest() {
        LitemallSeasonRule broken = autumn();
        broken.setSeasonKey("broken");
        broken.setTerms("[\"blanket\"]");
        when(ruleMapper.selectAll(true)).thenReturn(new ArrayList<>(List.of(broken, autumn())));
        when(searchService.search(anyString(), anyInt(), anyInt(), any(), any()))
                .thenThrow(new IllegalStateException("OCS down"));

        List<SeasonScoringService.SeasonRunResult> results = service.scoreAll(DAY);

        // The term lookup fails inside discover(), which swallows per-term — so both seasons still
        // report, with nothing found. The point is that scoreAll completes rather than throwing.
        assertEquals(2, results.size());
        verify(signalResolver).invalidate();
    }

    /**
     * Pins the shape that actually broke this in production: OCS document ids are strings, so a
     * hit carries {@code "10010060"}, not {@code 10010060}. Accepting only Number discarded every
     * hit and the scorer reported "scanned 0" against thousands of real matches.
     */
    @Test
    public void aSearchHitsIdIsReadWhetherItArrivesAsStringOrNumber() {
        assertEquals(10010060, SeasonScoringService.goodsId("10010060"));
        assertEquals(10010060, SeasonScoringService.goodsId(10010060));
        assertNull(SeasonScoringService.goodsId("not-a-number"));
        assertNull(SeasonScoringService.goodsId(null));
        assertNull(SeasonScoringService.goodsId(""));
    }
    // ---- term-anchored discovery ------------------------------------------

    /**
     * The live miss: "All-Season Sofa Cover" reached the autumn rail because the exact query for
     * a term found nothing and the searcher fell back to relaxed relevance. A relaxed result set
     * is a set of guesses, so it contributes nothing — and says so in the run result.
     */
    @Test
    public void aRelaxedResultSetContributesNothing() {
        when(searchService.search(eq("blanket"), anyInt(), anyInt(), any(), any()))
                .thenReturn(searchResult(true,
                        hitRow(201, "All-Season Sofa Cover"),
                        hitRow(202, "Chunky Knit Blanket")));
        givenGoods(202, "Chunky Knit Blanket", "40.00", "16.00", 50);

        SeasonScoringService.SeasonRunResult result = service.scoreSeason(autumn(), DAY);

        verify(candidateMapper, never()).upsertProposal(any());
        assertEquals(0, result.scanned());
        assertEquals(2, result.discardedRelaxed(),
                "even the on-title hit is dropped: the whole set came from a relaxed query");
    }

    /** The index also matches descriptions and category names; the rail wants the title. */
    @Test
    public void aHitWithoutTheTermInItsTitleIsDiscarded() {
        when(searchService.search(eq("blanket"), anyInt(), anyInt(), any(), any()))
                .thenReturn(searchResult(false,
                        hitRow(301, "Digital Cable Organizer"),
                        hitRow(302, "Chunky Knit Blanket"),
                        hitRow(303, null)));
        givenGoods(302, "Chunky Knit Blanket", "40.00", "16.00", 50);

        SeasonScoringService.SeasonRunResult result = service.scoreSeason(autumn(), DAY);

        assertEquals(302, captureUpsert().getGoodsId());
        assertEquals(1, result.scanned());
        assertEquals(2, result.discardedOffTitle(),
                "an off-title hit and a title-less hit are both unverifiable");
    }

    /** The other live miss: a "Summer Cooling ... Blanket" matched autumn on the word blanket. */
    @Test
    public void anExclusionTermInTheTitleVetoesAnOtherwiseMatchingHit() {
        LitemallSeasonRule rule = autumn();
        rule.setTerms("[\"blanket\",\"-summer\"]");
        when(searchService.search(eq("blanket"), anyInt(), anyInt(), any(), any()))
                .thenReturn(searchResult(false,
                        hitRow(401, "Cartoon-Printed Summer Cooling Air-Conditioning Blanket"),
                        hitRow(402, "Chunky Knit Blanket")));
        givenGoods(402, "Chunky Knit Blanket", "40.00", "16.00", 50);

        SeasonScoringService.SeasonRunResult result = service.scoreSeason(rule, DAY);

        assertEquals(402, captureUpsert().getGoodsId());
        assertEquals(1, result.discardedExcluded());
        // An exclusion term is never searched for: it would only ever find what it rejects.
        verify(searchService, never()).search(eq("summer"), anyInt(), anyInt(), any(), any());
    }

    // ---- quantile tiers -----------------------------------------------------

    /**
     * Ten candidates with a spread of margins: with hot = top 10% and featured = top 35%, exactly
     * one is hot and three more are featured, whatever the absolute scores happen to be. The
     * cuts are reported so an operator can see where the bar fell.
     */
    @Test
    public void tiersSplitTheRunByQuantileAndTheCutsAreReported() {
        Map<String, Object>[] rows = new Map[10];
        for (int i = 0; i < 10; i++) {
            int id = 500 + i;
            rows[i] = hitRow(id, "Wool Blanket " + i);
            // margin rises with i: cost falls from 30 to 3 against a 40 retail (all clear the 15% markdown gate)
            givenGoods(id, "Wool Blanket " + i, "40.00", String.valueOf(30 - 3 * i) + ".00", 50);
        }
        when(searchService.search(eq("blanket"), anyInt(), anyInt(), any(), any()))
                .thenReturn(searchResult(false, rows));

        SeasonScoringService.SeasonRunResult result = service.scoreSeason(autumn(), DAY);

        ArgumentCaptor<LitemallSeasonCandidate> captor =
                ArgumentCaptor.forClass(LitemallSeasonCandidate.class);
        verify(candidateMapper, org.mockito.Mockito.times(10)).upsertProposal(captor.capture());
        Map<String, Integer> byTier = new HashMap<>();
        for (LitemallSeasonCandidate row : captor.getAllValues()) {
            byTier.merge(row.getTier(), 1, Integer::sum);
        }
        assertEquals(1, byTier.get(LitemallSeasonCandidate.TIER_HOT));
        assertEquals(3, byTier.get(LitemallSeasonCandidate.TIER_FEATURED));
        assertEquals(6, byTier.get(LitemallSeasonCandidate.TIER_WATCH));
        assertEquals(4, result.published(), "auto-tier featured = the top 35% publish");
        assertNotNull(result.hotCut());
        assertNotNull(result.featuredCut());
        assertTrue(result.hotCut().compareTo(result.featuredCut()) >= 0);
    }
}
