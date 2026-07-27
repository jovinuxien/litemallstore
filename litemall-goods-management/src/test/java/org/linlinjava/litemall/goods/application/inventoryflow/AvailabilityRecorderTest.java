package org.linlinjava.litemall.goods.application.inventoryflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.db.dao.LitemallProductMetricDailyMapper;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** VANISHED pids drop today's availability to 0 — including already-soft-deleted goods. */
public class AvailabilityRecorderTest {

    private LitemallCjLinkageMapper linkageMapper;
    private LitemallProductMetricDailyMapper metricMapper;
    private AvailabilityRecorder recorder;

    @BeforeEach
    public void setUp() {
        linkageMapper = mock(LitemallCjLinkageMapper.class);
        metricMapper = mock(LitemallProductMetricDailyMapper.class);
        recorder = new AvailabilityRecorder(linkageMapper, metricMapper);
    }

    @Test
    public void vanishedPidMarksTodayUnavailable() {
        when(linkageMapper.findAnyGoodsIdByCjPid("gone-pid")).thenReturn(42);

        ProductFlowEvent event = new ProductFlowEvent(ProductFlowEvent.Kind.VANISHED, "gone-pid");
        assertEquals(event, recorder.markVanished(event));

        verify(metricMapper).upsertAvailability(eq(42), eq(LocalDate.now()), eq(false));
    }

    @Test
    public void neverPromotedPidIsSkippedQuietly() {
        when(linkageMapper.findAnyGoodsIdByCjPid("unknown")).thenReturn(null);

        recorder.markVanished(new ProductFlowEvent(ProductFlowEvent.Kind.VANISHED, "unknown"));

        verify(metricMapper, never()).upsertAvailability(any(), any(), anyBoolean());
    }
}
