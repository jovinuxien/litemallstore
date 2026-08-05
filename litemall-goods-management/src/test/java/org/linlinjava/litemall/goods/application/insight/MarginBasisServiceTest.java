package org.linlinjava.litemall.goods.application.insight;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.goods.application.goods.CatalogGoodsCountService;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Margin-basis honesty: category ids are subtree-expanded and deduped at query time,
 * junk ids are dropped, an empty request means the whole on-sale catalog, and the
 * mapper's NULL-when-uncosted discipline passes through untouched (null, never 0).
 */
public class MarginBasisServiceTest {

    private InsightMapper insightMapper;
    private CatalogGoodsCountService countService;
    private MarginBasisService service;

    @BeforeEach
    public void setUp() {
        insightMapper = mock(InsightMapper.class);
        countService = mock(CatalogGoodsCountService.class);
        service = new MarginBasisService(insightMapper, countService);
        when(insightMapper.selectMarginBasis(anyList(), anyList())).thenReturn(new HashMap<>());
    }

    @Test
    public void expandsCategorySubtreesAndDedupes() {
        when(countService.subtreeIds(100)).thenReturn(List.of(100, 110, 111));
        when(countService.subtreeIds(200)).thenReturn(List.of(200, 110));

        service.basis(null, List.of(100, 200));

        ArgumentCaptor<List<Integer>> categories = ArgumentCaptor.forClass(List.class);
        verify(insightMapper).selectMarginBasis(anyList(), categories.capture());
        assertEquals(List.of(100, 110, 111, 200), categories.getValue());
    }

    @Test
    public void dropsNullNonPositiveAndDuplicateGoodsIds() {
        service.basis(Arrays.asList(7, null, 0, -3, 7, 9), null);

        ArgumentCaptor<List<Integer>> goods = ArgumentCaptor.forClass(List.class);
        verify(insightMapper).selectMarginBasis(goods.capture(), anyList());
        assertEquals(List.of(7, 9), goods.getValue());
        verify(countService, never()).subtreeIds(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    public void emptyScopeMeansWholeCatalog() {
        service.basis(null, null);

        ArgumentCaptor<List<Integer>> goods = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<Integer>> categories = ArgumentCaptor.forClass(List.class);
        verify(insightMapper).selectMarginBasis(goods.capture(), categories.capture());
        assertEquals(List.of(), goods.getValue());
        assertEquals(List.of(), categories.getValue());
    }

    @Test
    public void passesMapperRowThroughWithNullsPreserved() {
        Map<String, Object> row = new HashMap<>();
        row.put("onSaleCount", 3L);
        row.put("costedCount", 2L);
        row.put("uncostedCount", 1L);
        row.put("maxCostRatio", new BigDecimal("0.8000"));
        row.put("minRetailPrice", null);
        when(insightMapper.selectMarginBasis(anyList(), anyList())).thenReturn(row);

        Map<String, Object> data = service.basis(List.of(1, 2, 3), null);

        assertEquals(3L, data.get("onSaleCount"));
        assertEquals(2L, data.get("costedCount"));
        assertEquals(1L, data.get("uncostedCount"));
        assertEquals(new BigDecimal("0.8000"), data.get("maxCostRatio"));
        assertNull(data.get("minRetailPrice"));
    }
}
