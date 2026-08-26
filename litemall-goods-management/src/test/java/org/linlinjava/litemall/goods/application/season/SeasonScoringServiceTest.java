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

    /** A strong, profitable, in-stock product — scores well above the featured bar. */
    private void givenOneStrongHit(int goodsId) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", goodsId);
        Map<String, Object> result = new HashMap<>();
        result.put("goodsList", List.of(row));
        result.put("total", 1);
        when(searchService.search(eq("blanket"), anyInt(), anyInt(), any(), any()))
                .thenReturn(result);

        LitemallGoods goods = new LitemallGoods();
        goods.setId(goodsId);
        goods.setName("Chunky Knit Throw");
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
}
