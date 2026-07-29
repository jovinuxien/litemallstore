package org.linlinjava.litemall.goods.application.inventoryflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.dao.LitemallRetireCandidateMapper;
import org.linlinjava.litemall.db.domain.LitemallRetireCandidate;
import org.linlinjava.litemall.goods.infrastructure.configuration.InventoryFlowProperties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Governor sizing: nothing at/below target, top-up to exactly the overage above it, existing
 * same-day proposals count toward the batch, and vetoed proposals don't (the pool keeps
 * being consumed until the need is met or it runs dry).
 */
public class RetirementGovernorTest {

    private InsightMapper insightMapper;
    private LitemallRetireCandidateMapper retireMapper;
    private RetireCandidateScorer scorer;
    private InventoryFlowProperties properties;
    private RetirementGovernor governor;

    @BeforeEach
    public void setUp() {
        insightMapper = mock(InsightMapper.class);
        retireMapper = mock(LitemallRetireCandidateMapper.class);
        scorer = mock(RetireCandidateScorer.class);
        properties = new InventoryFlowProperties();
        properties.setCatalogTarget(100);
        governor = new RetirementGovernor(insightMapper, retireMapper, scorer, properties);
        when(retireMapper.selectByDay(any(), any())).thenReturn(List.of());
    }

    private static List<Map<String, Object>> pool(int... goodsIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int id : goodsIds) {
            Map<String, Object> row = new HashMap<>();
            row.put("goodsId", id);
            row.put("salesQty", 0L);
            row.put("views", 0L);
            rows.add(row);
        }
        return rows;
    }

    @Test
    public void atOrBelowTargetProposesNothing() {
        when(insightMapper.countOnSaleCj()).thenReturn(100L);
        governor.governAfterCatalogRun();
        verify(insightMapper, never()).selectWeakestOnSale(anyInt());
        verify(scorer, never()).proposeWeak(anyInt(), anyList());
    }

    @Test
    public void overageTopsUpExactlyTheNeed() {
        when(insightMapper.countOnSaleCj()).thenReturn(103L); // overage 3
        when(insightMapper.selectWeakestOnSale(anyInt())).thenReturn(pool(1, 2, 3, 4, 5));
        when(scorer.proposeWeak(anyInt(), anyList())).thenReturn(true);

        governor.governAfterCatalogRun();

        verify(scorer, times(3)).proposeWeak(anyInt(), anyList());
    }

    @Test
    public void sameDayProposalsCountTowardTheBatch() {
        when(insightMapper.countOnSaleCj()).thenReturn(103L); // overage 3
        LitemallRetireCandidate today = new LitemallRetireCandidate();
        today.setGoodsId(9);
        today.setStatus(LitemallRetireCandidate.STATUS_PROPOSED);
        LitemallRetireCandidate decided = new LitemallRetireCandidate();
        decided.setGoodsId(8);
        decided.setStatus(LitemallRetireCandidate.STATUS_DISMISSED);
        when(retireMapper.selectByDay(any(), any())).thenReturn(List.of(today, decided));
        when(insightMapper.selectWeakestOnSale(anyInt())).thenReturn(pool(1, 2, 3, 4));
        when(scorer.proposeWeak(anyInt(), anyList())).thenReturn(true);

        governor.governAfterCatalogRun();

        // need = 3 − 1 already-proposed = 2
        verify(scorer, times(2)).proposeWeak(anyInt(), anyList());
    }

    @Test
    public void goodsAlreadyTouchedTodayIsSkippedInThePool() {
        when(insightMapper.countOnSaleCj()).thenReturn(101L); // overage 1... but covered below
        LitemallRetireCandidate dismissedToday = new LitemallRetireCandidate();
        dismissedToday.setGoodsId(1);
        dismissedToday.setStatus(LitemallRetireCandidate.STATUS_DISMISSED);
        when(retireMapper.selectByDay(any(), any())).thenReturn(List.of(dismissedToday));
        when(insightMapper.selectWeakestOnSale(anyInt())).thenReturn(pool(1, 2));
        when(scorer.proposeWeak(anyInt(), anyList())).thenReturn(true);

        governor.governAfterCatalogRun();

        verify(scorer, never()).proposeWeak(eq(1), anyList());
        verify(scorer).proposeWeak(eq(2), anyList());
    }

    @Test
    public void vetoedProposalsKeepConsumingThePool() {
        when(insightMapper.countOnSaleCj()).thenReturn(102L); // overage 2
        when(insightMapper.selectWeakestOnSale(anyInt())).thenReturn(pool(1, 2, 3, 4));
        when(scorer.proposeWeak(eq(1), anyList())).thenReturn(false); // e.g. cooldown veto
        when(scorer.proposeWeak(eq(2), anyList())).thenReturn(true);
        when(scorer.proposeWeak(eq(3), anyList())).thenReturn(true);

        governor.governAfterCatalogRun();

        verify(scorer, times(3)).proposeWeak(anyInt(), anyList()); // 1 vetoed + 2 landed
        verify(scorer, never()).proposeWeak(eq(4), anyList());
    }
}
