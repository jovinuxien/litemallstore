package org.linlinjava.litemall.goods.application.promo;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.dao.LitemallPromoCandidateMapper;
import org.linlinjava.litemall.db.domain.LitemallPromoCandidate;
import org.linlinjava.litemall.goods.application.insight.InsightService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Admin reads/decisions: the list defaults to the latest scored day and resolves leaf
 * categories to L1 roots; dismiss/consume are CAS flips from {@code proposed} with
 * errno 653 on a decided/missing row; consume records the created promotion's id.
 */
public class PromoCandidateAdminServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 8);

    private InsightMapper insightMapper;
    private LitemallPromoCandidateMapper candidateMapper;
    private CategoryRootResolver rootResolver;
    private PromoCandidateAdminService service;

    @BeforeEach
    public void setUp() {
        insightMapper = mock(InsightMapper.class);
        candidateMapper = mock(LitemallPromoCandidateMapper.class);
        rootResolver = mock(CategoryRootResolver.class);
        service = new PromoCandidateAdminService(insightMapper, candidateMapper, rootResolver,
                mock(PromoCandidateNightlyTask.class));
    }

    @Test
    public void listDefaultsToLatestDayAndResolvesRootsAndJson() {
        when(candidateMapper.selectLatestDay("coupon")).thenReturn(DAY);
        Map<String, Object> row = new HashMap<>();
        row.put("goodsId", 42);
        row.put("categoryId", 1008);
        row.put("suggestion", "{\"scopeType\":\"goods\",\"discount\":10}");
        row.put("reasons", "[\"margin 60%\"]");
        when(insightMapper.selectPromoCandidateRows("coupon", DAY, null)).thenReturn(List.of(row));
        when(rootResolver.rootOf(1008)).thenReturn(9000);

        Map<String, Object> data = service.list("coupon", null, null);

        assertEquals("2026-08-08", data.get("day"));
        @SuppressWarnings("unchecked")
        Map<String, Object> out = ((List<Map<String, Object>>) data.get("list")).get(0);
        assertEquals(9000, out.get("categoryId"));
        assertEquals("goods", ((Map<?, ?>) out.get("suggestion")).get("scopeType"));
        assertEquals(List.of("margin 60%"), out.get("reasons"));
    }

    @Test
    public void listWithNoScoredDaysIsEmptyNotAnError() {
        when(candidateMapper.selectLatestDay("groupon")).thenReturn(null);
        Map<String, Object> data = service.list("groupon", null, null);
        assertNull(data.get("day"));
        assertEquals(List.of(), data.get("list"));
    }

    @Test
    public void consumeFlipsAndRecordsTheCreatedId() {
        LitemallPromoCandidate candidate = new LitemallPromoCandidate();
        candidate.setId(7);
        candidate.setStatus(LitemallPromoCandidate.STATUS_PROPOSED);
        candidate.setDay(DAY);
        when(candidateMapper.selectLatestByKindAndGoods("coupon", 42)).thenReturn(candidate);
        when(candidateMapper.updateStatus(eq(7), anyString(), anyString(), any())).thenReturn(1);

        InsightService.CandidateActionResult result = service.consume("coupon", 42, null, 15);

        assertNull(result.error());
        verify(candidateMapper).updateStatus(7, LitemallPromoCandidate.STATUS_PROPOSED,
                LitemallPromoCandidate.STATUS_CONSUMED, 15);
    }

    @Test
    public void decidedOrMissingRowIs653() {
        when(candidateMapper.selectLatestByKindAndGoods("coupon", 42)).thenReturn(null);
        InsightService.CandidateActionResult missing = service.dismiss("coupon", 42, null);
        assertEquals(InsightService.ERRNO_CANDIDATE, missing.errno());

        LitemallPromoCandidate candidate = new LitemallPromoCandidate();
        candidate.setId(7);
        candidate.setStatus(LitemallPromoCandidate.STATUS_PROPOSED);
        candidate.setDay(DAY);
        when(candidateMapper.selectLatestByKindAndGoods("coupon", 42)).thenReturn(candidate);
        when(candidateMapper.updateStatus(anyInt(), anyString(), anyString(), any())).thenReturn(0);
        InsightService.CandidateActionResult raced = service.dismiss("coupon", 42, null);
        assertEquals(InsightService.ERRNO_CANDIDATE, raced.errno());
    }
}
