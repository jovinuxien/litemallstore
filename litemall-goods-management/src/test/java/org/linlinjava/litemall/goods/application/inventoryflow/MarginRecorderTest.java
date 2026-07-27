package org.linlinjava.litemall.goods.application.inventoryflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.dao.LitemallProductMetricDailyMapper;
import org.linlinjava.litemall.db.domain.LitemallProductMetricDaily;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Metric upsert semantics: same-day re-records go through the idempotent (goods, day)
 * upsert; missing cost stays NULL (never a fake 0 margin); the day's engagement is
 * bulk-loaded exactly once per day (the footprint table has no goods_id index).
 */
public class MarginRecorderTest {

    private LitemallProductMetricDailyMapper metricMapper;
    private InsightMapper insightMapper;
    private MarginRecorder recorder;

    @BeforeEach
    public void setUp() {
        metricMapper = mock(LitemallProductMetricDailyMapper.class);
        insightMapper = mock(InsightMapper.class);
        InventoryContextEnricher enricher = mock(InventoryContextEnricher.class);
        when(insightMapper.selectDailyViews(any())).thenReturn(
                List.of(Map.of("goodsId", 42, "cnt", 7L)));
        when(insightMapper.selectDailySales(any())).thenReturn(
                List.of(Map.of("goodsId", 42, "cnt", 3L)));
        recorder = new MarginRecorder(metricMapper, insightMapper, enricher);
    }

    private static ProductInventoryContext ctx(Integer goodsId, ProductFlowEvent.Kind kind,
                                               BigDecimal cost, BigDecimal marginPct) {
        return new ProductInventoryContext(new ProductFlowEvent(kind, "pid-1"),
                goodsId, new BigDecimal("12.50"), cost, marginPct, 100, null, null);
    }

    @Test
    public void recordsTodayRowWithEngagementAndMargin() {
        recorder.record(ctx(42, ProductFlowEvent.Kind.UPDATED,
                new BigDecimal("10.00"), new BigDecimal("20.00")));

        ArgumentCaptor<LitemallProductMetricDaily> captor =
                ArgumentCaptor.forClass(LitemallProductMetricDaily.class);
        verify(metricMapper).upsert(captor.capture());
        LitemallProductMetricDaily m = captor.getValue();
        assertEquals(Integer.valueOf(42), m.getGoodsId());
        assertEquals(LocalDate.now(), m.getDay());
        assertEquals(new BigDecimal("10.00"), m.getCost());
        assertEquals(new BigDecimal("20.00"), m.getMarginPct());
        assertEquals(Integer.valueOf(7), m.getViews());
        assertEquals(Integer.valueOf(3), m.getSalesQty());
        assertTrue(m.getAvailable());
    }

    @Test
    public void missingCostStaysNullNeverZero() {
        recorder.record(ctx(42, ProductFlowEvent.Kind.UPDATED, null, null));

        ArgumentCaptor<LitemallProductMetricDaily> captor =
                ArgumentCaptor.forClass(LitemallProductMetricDaily.class);
        verify(metricMapper).upsert(captor.capture());
        assertNull(captor.getValue().getCost());
        assertNull(captor.getValue().getMarginPct());
    }

    @Test
    public void sameDayRecordsLoadEngagementOnceAndUpsertTwice() {
        recorder.record(ctx(42, ProductFlowEvent.Kind.UPDATED,
                new BigDecimal("10.00"), new BigDecimal("20.00")));
        recorder.record(ctx(42, ProductFlowEvent.Kind.UPDATED,
                new BigDecimal("10.00"), new BigDecimal("20.00")));

        verify(metricMapper, times(2)).upsert(any());
        verify(insightMapper, times(1)).selectDailyViews(any());
        verify(insightMapper, times(1)).selectDailySales(any());
    }

    @Test
    public void unresolvedContextIsSkipped() {
        recorder.record(ProductInventoryContext.unresolved(
                new ProductFlowEvent(ProductFlowEvent.Kind.UPDATED, "pid-x")));
        verify(metricMapper, never()).upsert(any());
    }
}
