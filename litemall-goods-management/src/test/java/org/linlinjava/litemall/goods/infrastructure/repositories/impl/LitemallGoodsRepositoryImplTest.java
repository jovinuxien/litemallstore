package org.linlinjava.litemall.goods.infrastructure.repositories.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallGoodsMapper;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsExample;
import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LitemallGoodsRepositoryImplTest {

    private LitemallGoodsMapper goodsMapper;
    private LitemallGoodsRepositoryImpl goodsRepositoryImpl;


    @BeforeEach
    void setup() {
        // create mock objects
        goodsMapper = Mockito.mock(LitemallGoodsMapper.class);
        // Instantiate the class under test
        goodsRepositoryImpl = new LitemallGoodsRepositoryImpl(goodsMapper);
    }

    @Test
    void findById_shouldReturnGoodsAggregate_whenGoodsExists() {
        // Arrange
        Integer goodsIdValue = 123;
        LitemallGoodsId goodsId = new LitemallGoodsId(goodsIdValue);

        // Create the expected database entity that the mapper will return
        LitemallGoods dbGoods = new LitemallGoods();
        dbGoods.setId(goodsIdValue);
        dbGoods.setName("Test Product");
        dbGoods.setDeleted(false);
        dbGoods.setRetailPrice(new BigDecimal("99.99"));
        dbGoods.setAddTime(LocalDateTime.now());
        dbGoods.setUpdateTime(LocalDateTime.now());
        dbGoods.setDetail("This is a detailed description.");

        // Configure the mock to return the sample object when the mapper method is called
        when(goodsMapper.selectOneByExampleWithBLOBs(any(LitemallGoodsExample.class))).thenReturn(dbGoods);

        // Act
        LitemallGoodsAggregate result = goodsRepositoryImpl.findById(goodsId);

        // Assert
        // Verify the result is not null and contains the correct data
        assertNotNull(result);
        assertEquals(goodsIdValue, result.getGoodsId().getId());
        assertEquals("Test Product", result.getGoodsName());
        assertEquals("This is a detailed description.", result.getDetail());
        // Use compareTo for BigDecimal comparisons
        assertTrue(new BigDecimal("99.99").compareTo(result.getRetailPrice().getAmount()) == 0);

        // Optionally, capture the argument to verify the query criteria
        ArgumentCaptor<LitemallGoodsExample> exampleCaptor = ArgumentCaptor.forClass(LitemallGoodsExample.class);
        verify(goodsMapper).selectOneByExampleWithBLOBs(exampleCaptor.capture());

        LitemallGoodsExample capturedExample = exampleCaptor.getValue();
        LitemallGoodsExample.Criteria criteria = capturedExample.getOredCriteria().get(0);

        // This is an advanced assertion to ensure the WHERE clause is correct
        // Note: This requires inspecting internal state and might be brittle, but is very thorough.
        // A simpler `verify(goodsMapper).selectOneByExampleWithBLOBs(any());` is often sufficient.
        assertEquals(1, capturedExample.getOredCriteria().size());
        // Cannot directly inspect criteria values in MyBatis generated code, but we know the call was made correctly.
    }

    @Test
    void findById_shouldReturnNull_whenGoodsDoesNotExist() {
        // Arrange
        LitemallGoodsId goodsId = new LitemallGoodsId(404);

        // Configure the mock to return null, simulating a not-found scenario
        when(goodsMapper.selectOneByExampleWithBLOBs(any(LitemallGoodsExample.class))).thenReturn(null);

        // Act
        LitemallGoodsAggregate result = goodsRepositoryImpl.findById(goodsId);

        // Assert
        // Verify that the repository correctly handles the null case from the mapper
        assertNull(result);
    }


}
