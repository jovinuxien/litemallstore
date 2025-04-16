package org.linlinjava.litemall.order.domain.dao;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallOrderMapper;
import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.order.AbstractMyBatisTest;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LitemallOrderMapperTest extends AbstractMyBatisTest {

    @Autowired
    private LitemallOrderMapper orderMapper;

    @Test
    void shouldFindOrderById() {
        // Given
        Long orderId = 1L;

        // When
        LitemallOrder order = orderMapper.selectByPrimaryKey(Math.toIntExact(orderId));

        // Then
        assertNotNull(order);
        assertEquals("Test Order", order.getOrderSn());
    }
}
