package org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.goods.infrastructure.acl.cache.CjRawCacheRepository;
import org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.api.product.CJProductClient;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProduct;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductData;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave 26 Phase 2 deliverable 5: a QPS-rejected list page must be RETRIED, not silently dropped.
 *
 * <p>Before this, {@code fetchByCategory} broke on the first failed page — and CJ rejects a
 * substantial share of {@code /product/list} calls with "QPS limit is 1 time/1second" even at the
 * configured 3s pace (~17% of 252 calls, measured 2026-08-13). A rejection on page 1 therefore
 * returned ZERO products for that leaf behind a single WARN, making every nightly catalog run
 * quietly lossy across ~540 leaves.
 *
 * <p>Pace is set to 1s so the paced limiter doesn't dominate the test clock; the retry path is
 * what's under test, not the pacing.
 */
public class CJProductServiceRetryTest {

    private CJProductClient client;
    private CjRawCacheRepository rawCache;
    private CJProductService service;

    @BeforeEach
    public void setUp() throws Exception {
        client = mock(CJProductClient.class);
        rawCache = mock(CjRawCacheRepository.class);
        when(rawCache.get(anyString(), any())).thenReturn(Optional.empty());

        CJDropshippingConfig config = new CJDropshippingConfig();
        config.setFetchPaceSeconds(1);
        config.setFetchRetries(3);

        service = new CJProductService();
        inject("productClient", client);
        inject("rawCache", rawCache);
        inject("config", config);
    }

    private void inject(String field, Object value) throws Exception {
        Field f = CJProductService.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(service, value);
    }

    private CJProductDataResponse page(int total, String... pids) {
        CJProductDataResponse resp = new CJProductDataResponse();
        CJProductData data = new CJProductData();
        data.setTotal(total);
        data.setList(java.util.Arrays.stream(pids).map(p -> {
            CJProduct cj = new CJProduct();
            cj.setPid(p);
            return cj;
        }).toList());
        resp.setData(data);
        return resp;
    }

    @Test
    public void recoversAfterTransientRejections() {
        when(client.getProductList(anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Too Many Requests  QPS limit is 1 time/1second"))
                .thenThrow(new RuntimeException("Too Many Requests  QPS limit is 1 time/1second"))
                .thenReturn(page(1, "PID-1"));

        List<CJProduct> out = service.fetchByCategory("LEAF", 10, 20);

        assertEquals(1, out.size(), "the page must survive two transient rejections");
        assertEquals("PID-1", out.get(0).getPid());
        verify(client, times(3)).getProductList(anyString(), anyInt(), anyInt());
        assertEquals(2, service.getAndResetRejections(),
                "both rejections are counted even though the retry recovered — the reported rate "
                        + "must reflect what CJ did, not what we salvaged");
    }

    @Test
    public void givesUpAfterConfiguredAttemptsWithoutThrowing() {
        when(client.getProductList(anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("QPS limit is 1 time/1second"));

        List<CJProduct> out = service.fetchByCategory("LEAF", 10, 20);

        assertTrue(out.isEmpty(), "an unrecoverable page degrades to empty, never an exception");
        verify(client, times(3)).getProductList(anyString(), anyInt(), anyInt());
        assertEquals(3, service.getAndResetRejections());
    }

    @Test
    public void happyPathIsUnchangedAndCostsOneCall() {
        when(client.getProductList(anyString(), anyInt(), anyInt())).thenReturn(page(1, "PID-1"));

        List<CJProduct> out = service.fetchByCategory("LEAF", 10, 20);

        assertEquals(1, out.size());
        verify(client, times(1)).getProductList(anyString(), anyInt(), anyInt());
        assertEquals(0, service.getAndResetRejections(), "a clean run reports no rejections");
    }

    @Test
    public void rejectionCounterResetsSoRunsDoNotAccumulate() {
        when(client.getProductList(anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("QPS limit is 1 time/1second"));

        service.fetchByCategory("LEAF", 10, 20);
        assertEquals(3, service.getAndResetRejections());
        assertEquals(0, service.getAndResetRejections(), "second read of the same run reports zero");
    }
}
