package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.util.exception.product.LitemallGoodsServiceUnavailableException;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.GoodsServiceFeignClient;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the goods ACL. Asserts that the facade unwraps a successful
 * {@code ApiResponse} and — crucially — converts the circuit-breaker fallback's
 * error envelope (errno != 0) into {@link LitemallGoodsServiceUnavailableException}
 * so a goods-management outage fails placement cleanly.
 */
@ExtendWith(MockitoExtension.class)
class LitemallGoodsFacadeImplTest {

    @Mock
    private GoodsServiceFeignClient feignClient;

    @InjectMocks
    private LitemallGoodsFacadeImpl facade;

    @Test
    void getGoodsProduct_unwrapsSuccessfulResponse() {
        LitemallGoodsProductAggregate product = new LitemallGoodsProductAggregate();
        ApiResponse<LitemallGoodsProductAggregate> ok = new ApiResponse<>();
        ok.setErrno(0);
        ok.setData(product);
        when(feignClient.getGoodsProductAggregate(42)).thenReturn(ok);

        assertSame(product, facade.getGoodsProduct(new LitemallGoodsProductId(42)));
    }

    @Test
    void getGoodsProduct_translatesFallbackErrorIntoUnavailable() {
        // Mimics GoodsServiceFeignClientFallbackFactory returning errno 503.
        ApiResponse<LitemallGoodsProductAggregate> down = new ApiResponse<>();
        down.setErrno(503);
        down.setErrmsg("goods-service unavailable");
        when(feignClient.getGoodsProductAggregate(42)).thenReturn(down);

        assertThrows(LitemallGoodsServiceUnavailableException.class,
                () -> facade.getGoodsProduct(new LitemallGoodsProductId(42)));
    }

    @Test
    void getGoodsProduct_translatesTransportFailureIntoUnavailable() {
        when(feignClient.getGoodsProductAggregate(42))
                .thenThrow(new RuntimeException("connect timed out"));

        assertThrows(LitemallGoodsServiceUnavailableException.class,
                () -> facade.getGoodsProduct(new LitemallGoodsProductId(42)));
    }

    // --- restoreStock: compensating release used from rollback/cancel paths ---

    @Test
    void restoreStock_unwrapsSuccessfulResponse() {
        ApiResponse<Map<Integer, Boolean>> ok = new ApiResponse<>();
        ok.setErrno(0);
        ok.setData(Map.of(5, true));
        when(feignClient.batchRestoreStock(anyList())).thenReturn(ok);

        assertEquals(Map.of(5, true), facade.restoreStock(Map.of(5, 2)));
    }

    @Test
    void restoreStock_swallowsErrorEnvelope_returningEmpty() {
        // Mimics the circuit-breaker fallback (errno 503). Unlike reduceStock, the
        // compensating restore must NOT throw — it is called from rollback paths.
        ApiResponse<Map<Integer, Boolean>> down = new ApiResponse<>();
        down.setErrno(503);
        down.setErrmsg("goods-service unavailable");
        when(feignClient.batchRestoreStock(anyList())).thenReturn(down);

        assertTrue(facade.restoreStock(Map.of(5, 2)).isEmpty());
    }

    @Test
    void restoreStock_swallowsTransportFailure_returningEmpty() {
        when(feignClient.batchRestoreStock(anyList()))
                .thenThrow(new RuntimeException("connect timed out"));

        assertTrue(facade.restoreStock(Map.of(5, 2)).isEmpty());
    }

    @Test
    void restoreStock_emptyInputShortCircuits() {
        assertTrue(facade.restoreStock(Collections.<Integer, Integer>emptyMap()).isEmpty());
    }
}
