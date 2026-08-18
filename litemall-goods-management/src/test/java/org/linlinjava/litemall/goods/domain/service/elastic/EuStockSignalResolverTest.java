package org.linlinjava.litemall.goods.domain.service.elastic;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.service.LitemallCjProductService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * eu_flag matrix: a measured-EU pid flags, an unmeasured or zero-stock pid does not, local goods
 * (no pid) never flag, the snapshot is memoized rather than re-queried per document, and a mapper
 * failure degrades to the previous snapshot instead of killing indexing.
 */
public class EuStockSignalResolverTest {

    private LitemallCjProductService cjProductStore;
    private EuStockSignalResolver resolver;

    @BeforeEach
    public void setUp() {
        cjProductStore = mock(LitemallCjProductService.class);
        resolver = new EuStockSignalResolver(cjProductStore);
    }

    @Test
    public void aPidWithMeasuredEuStockFlags() {
        when(cjProductStore.euStockedPids()).thenReturn(List.of("pid-de-1", "pid-de-2"));

        assertEquals(1, resolver.euFlag("pid-de-1"));
        assertEquals(1, resolver.euFlag("pid-de-2"));
    }

    /**
     * The query already excludes NULL (never probed) and 0, so both arrive here as "absent from the
     * set". The flag says "not known to hold EU stock" — it must never be read as "holds none".
     */
    @Test
    public void aPidNeverProbedOrWithoutEuStockDoesNotFlag() {
        when(cjProductStore.euStockedPids()).thenReturn(List.of("pid-de-1"));

        assertEquals(0, resolver.euFlag("pid-never-probed"));
    }

    /** Local goods carry no CJ pid: there is no warehouse reading to claim anything from. */
    @Test
    public void localGoodsWithoutAPidNeverFlag() {
        when(cjProductStore.euStockedPids()).thenReturn(List.of("pid-de-1"));

        assertEquals(0, resolver.euFlag(null));
        assertEquals(0, resolver.euFlag(""));
        assertEquals(0, resolver.euFlag("   "));
    }

    /**
     * A full reindex asks this once per document. Without memoization that is one query per
     * product — the reason the resolver holds a TTL snapshot at all.
     */
    @Test
    public void theSnapshotIsMemoizedAcrossDocuments() {
        when(cjProductStore.euStockedPids()).thenReturn(List.of("pid-de-1"));

        for (int i = 0; i < 50; i++) {
            resolver.euFlag("pid-de-1");
        }

        verify(cjProductStore, times(1)).euStockedPids();
    }

    /** Indexing must survive a bad EU read: the flag lags, the reindex still completes. */
    @Test
    public void aFailedRefreshDegradesToZeroRatherThanThrowing() {
        when(cjProductStore.euStockedPids()).thenThrow(new IllegalStateException("db down"));

        assertEquals(0, resolver.euFlag("pid-de-1"));
    }

    /** A null result set is treated as "nothing measured", not as an NPE mid-reindex. */
    @Test
    public void aNullResultSetIsTreatedAsNothingMeasured() {
        when(cjProductStore.euStockedPids()).thenReturn(null);

        assertEquals(0, resolver.euFlag("pid-de-1"));
    }
}
