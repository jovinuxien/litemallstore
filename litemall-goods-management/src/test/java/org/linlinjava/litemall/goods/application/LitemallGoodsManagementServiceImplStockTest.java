package org.linlinjava.litemall.goods.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.core.qcode.QCodeService;
import org.linlinjava.litemall.db.service.LitemallCartService;
import org.linlinjava.litemall.goods.application.goods.internal.LitemallGoodsManagementServiceImpl;
import org.linlinjava.litemall.goods.domain.model.repositories.LitemallBrandRepository;
import org.linlinjava.litemall.goods.domain.model.repositories.LitemallCatalogRepository;
import org.linlinjava.litemall.goods.domain.model.repositories.LitemallGoodsProductRepository;
import org.linlinjava.litemall.goods.domain.model.repositories.LitemallGoodsRepository;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.linlinjava.litemall.goods.infrastructure.messaging.source.GoodsIndexEventPublisher;
import org.linlinjava.litemall.goods.infrastructure.services.api.LitemallCatalogService;
import org.linlinjava.litemall.goods.infrastructure.services.api.LitemallGoodsServiceApi;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LitemallGoodsManagementServiceImplStockTest {

    private LitemallGoodsProductRepository goodsProductRepository;
    private LitemallGoodsManagementServiceImpl service;

    @BeforeEach
    void setup() {
        goodsProductRepository = Mockito.mock(LitemallGoodsProductRepository.class);
        service = new LitemallGoodsManagementServiceImpl(
                Mockito.mock(LitemallGoodsRepository.class),
                Mockito.mock(LitemallGoodsServiceApi.class),
                Mockito.mock(LitemallCatalogRepository.class),
                Mockito.mock(LitemallBrandRepository.class),
                Mockito.mock(QCodeService.class),
                Mockito.mock(LitemallCartService.class),
                Mockito.mock(LitemallCatalogService.class),
                Mockito.mock(LitemallGoodsProperties.class),
                Mockito.mock(GoodsIndexEventPublisher.class),
                goodsProductRepository);
    }

    @Test
    void reduceStock_returnsTrue_whenOneRowDecremented() {
        LitemallGoodsProductId productId = new LitemallGoodsProductId("7");
        when(goodsProductRepository.reduceStock(productId, (short) 2)).thenReturn(1);

        assertTrue(service.reduceStock(productId, (short) 2));
        verify(goodsProductRepository).reduceStock(productId, (short) 2);
    }

    @Test
    void reduceStock_returnsFalse_whenInsufficientStock() {
        LitemallGoodsProductId productId = new LitemallGoodsProductId("7");
        when(goodsProductRepository.reduceStock(productId, (short) 999)).thenReturn(0);

        assertFalse(service.reduceStock(productId, (short) 999));
    }

    @Test
    void restoreStock_returnsTrue_whenOneRowUpdated() {
        LitemallGoodsProductId productId = new LitemallGoodsProductId("7");
        when(goodsProductRepository.addStock(productId, (short) 2)).thenReturn(1);

        assertTrue(service.restoreStock(productId, (short) 2));
        verify(goodsProductRepository).addStock(productId, (short) 2);
    }

    @Test
    void restoreStock_returnsFalse_whenProductUnknown() {
        LitemallGoodsProductId productId = new LitemallGoodsProductId("404");
        when(goodsProductRepository.addStock(productId, (short) 2)).thenReturn(0);

        assertFalse(service.restoreStock(productId, (short) 2));
    }
}
