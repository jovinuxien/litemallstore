package org.linlinjava.litemall.goods.infrastructure.repositories.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.GoodsProductMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsProductMapper;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallMoney;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class LitemallGoodsProductRepositoryImplTest {

    private LitemallGoodsProductMapper goodsProductMapper;
    private GoodsProductMapper goodsMapper;
    private LitemallGoodsProductRepositoryImpl repository;

    @BeforeEach
    void setup() {
        goodsProductMapper = Mockito.mock(LitemallGoodsProductMapper.class);
        goodsMapper = Mockito.mock(GoodsProductMapper.class);
        repository = new LitemallGoodsProductRepositoryImpl(goodsProductMapper, goodsMapper);
    }

    @Test
    void reduceStock_returnsAffectedRows() {
        when(goodsMapper.reduceStock(7, (short) 2)).thenReturn(1);
        assertEquals(1, repository.reduceStock(new LitemallGoodsProductId("7"), (short) 2));
    }

    @Test
    void reduceStock_returnsZero_whenGuardRejects() {
        when(goodsMapper.reduceStock(7, (short) 999)).thenReturn(0);
        assertEquals(0, repository.reduceStock(new LitemallGoodsProductId("7"), (short) 999));
    }

    @Test
    void addStock_returnsAffectedRows() {
        when(goodsMapper.addStock(7, (short) 2)).thenReturn(1);
        assertEquals(1, repository.addStock(new LitemallGoodsProductId("7"), (short) 2));
    }

    @Test
    void convertToDomainModel_mapsParentGoodsId_notRowId() {
        LitemallGoodsProduct record = new LitemallGoodsProduct();
        record.setId(3);
        record.setGoodsId(1181000);
        record.setPrice(new BigDecimal("9.99"));
        record.setNumber(100);
        record.setAddTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setDeleted(false);

        LitemallGoodsProductAggregate aggregate = repository.convertToDomainModel(record);

        assertEquals("3", aggregate.getGoodsProductId().getId());
        assertEquals(Integer.valueOf(1181000), aggregate.getGoodsId().getId());
    }

    @Test
    void convertToDataModel_mapsParentGoodsId_notProductId() {
        LitemallGoodsProductAggregate aggregate = new LitemallGoodsProductAggregate();
        aggregate.setGoodsProductId(new LitemallGoodsProductId("3"));
        aggregate.setGoodsId(new LitemallGoodsId(1181000));
        aggregate.setPrice(new LitemallMoney(new BigDecimal("9.99")));
        aggregate.setNumber(100);

        LitemallGoodsProduct dataModel = repository.convertToDataModel(aggregate);

        assertEquals(Integer.valueOf(3), dataModel.getId());
        assertEquals(Integer.valueOf(1181000), dataModel.getGoodsId());
    }
}
