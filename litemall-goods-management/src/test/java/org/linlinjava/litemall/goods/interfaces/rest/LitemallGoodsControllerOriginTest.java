package org.linlinjava.litemall.goods.interfaces.rest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.goods.application.goods.WarehouseOriginService;
import org.linlinjava.litemall.goods.infrastructure.services.api.LitemallGoodsServiceApi;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave 28 §3.1 envelope: {@code {errno:0, data:{list:[{goodsId, originCountry}]}}} with rows only
 * for measured goods, an empty list for nothing/junk, and a typed 402 above the id cap — the SPA's
 * {@code catalogApi.goodsOrigin} unwraps exactly this. Plus the {@code /batch} cap raised by
 * gateway-api on 2026-08-22.
 */
public class LitemallGoodsControllerOriginTest {

    private WarehouseOriginService originService;
    private LitemallGoodsServiceApi goodsServiceApi;
    private LitemallGoodsController controller;

    @BeforeEach
    void setUp() {
        originService = Mockito.mock(WarehouseOriginService.class);
        goodsServiceApi = Mockito.mock(LitemallGoodsServiceApi.class);
        controller = new LitemallGoodsController();
        ReflectionTestUtils.setField(controller, "warehouseOriginService", originService);
        ReflectionTestUtils.setField(controller, "goodsServiceApi", goodsServiceApi);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }

    @Test
    void measuredGoodsAppearAsRowsAndTheOthersAreAbsent() {
        when(originService.originsOf(List.of(5, 6)))
                .thenReturn(List.of(new WarehouseOriginService.Origin(6, "DE")));

        Map<String, Object> env = asMap(controller.origin("5,6"));

        assertEquals(0, env.get("errno"));
        List<Map<String, Object>> list = (List<Map<String, Object>>) asMap(env.get("data")).get("list");
        assertEquals(1, list.size());
        assertEquals(6, list.get(0).get("goodsId"));
        assertEquals("DE", list.get(0).get("originCountry"));
    }

    @Test
    void missingOrJunkIdsAnswerAnEmptyListNotAnError() {
        when(originService.originsOf(anyCollection())).thenReturn(List.of());

        for (String ids : new String[]{null, "", " , ,x"}) {
            Map<String, Object> env = asMap(controller.origin(ids));
            assertEquals(0, env.get("errno"), "ids=" + ids);
            assertTrue(((List<?>) asMap(env.get("data")).get("list")).isEmpty(), "ids=" + ids);
        }
    }

    @Test
    void moreThanTheCapIsATypedRefusalWithNoLookup() {
        String ids = IntStream.rangeClosed(1, WarehouseOriginService.MAX_IDS + 1)
                .mapToObj(Integer::toString).collect(Collectors.joining(","));

        Map<String, Object> env = asMap(controller.origin(ids));

        assertEquals(402, env.get("errno"));
        assertTrue(String.valueOf(env.get("errmsg")).contains(String.valueOf(WarehouseOriginService.MAX_IDS)));
        verify(originService, never()).originsOf(anyCollection());
    }

    /** Duplicates do not count toward the cap — the cap bounds distinct reads. */
    @Test
    void exactlyTheCapAfterDedupIsServed() {
        String ids = IntStream.rangeClosed(1, WarehouseOriginService.MAX_IDS)
                .mapToObj(Integer::toString).collect(Collectors.joining(",")) + ",1,2,3";
        when(originService.originsOf(anyCollection())).thenReturn(List.of());

        assertEquals(0, asMap(controller.origin(ids)).get("errno"));
    }

    @Test
    void batchAboveTheCapIsRefusedBeforeAnyRead() {
        Set<Integer> ids = IntStream.rangeClosed(1, LitemallGoodsController.BATCH_MAX_IDS + 1)
                .boxed().collect(Collectors.toSet());

        Map<String, Object> env = asMap(controller.batchGoods(ids));

        assertEquals(402, env.get("errno"));
        verify(goodsServiceApi, never()).getAllGoodByIds(anyList());
    }

    /** At or under the cap the batch answer keeps its raw {goodsId: aggregate} map shape. */
    @Test
    void batchWithinTheCapStillAnswersTheRawMap() {
        when(goodsServiceApi.getAllGoodByIds(anyList())).thenReturn(List.of());

        Object out = controller.batchGoods(Set.of(1, 2, 3));

        assertTrue(out instanceof Map);
        assertTrue(((Map<?, ?>) out).isEmpty());
    }
}
