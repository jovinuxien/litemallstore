package org.linlinjava.litemall.goods.application.comment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallOrderGoodsMapper;
import org.linlinjava.litemall.db.dao.LitemallOrderMapper;
import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.db.domain.LitemallOrderExample;
import org.linlinjava.litemall.db.domain.LitemallOrderGoods;
import org.linlinjava.litemall.db.domain.LitemallOrderGoodsExample;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F17 eligibility: a review needs an unreviewed DELIVERED line of the goods bought by the caller.
 * The mappers are mocked; the assertions are on what the Example criteria BIND (user, statuses,
 * literal deleted=false, the pinned order) and on how the returned rows are judged.
 */
public class PurchaseVerificationServiceTest {

    private LitemallOrderMapper orderMapper;
    private LitemallOrderGoodsMapper orderGoodsMapper;
    private PurchaseVerificationService service;

    @BeforeEach
    void setUp() {
        orderMapper = Mockito.mock(LitemallOrderMapper.class);
        orderGoodsMapper = Mockito.mock(LitemallOrderGoodsMapper.class);
        service = new PurchaseVerificationService(orderMapper, orderGoodsMapper);
    }

    private static LitemallOrder order(int id) {
        LitemallOrder o = new LitemallOrder();
        o.setId(id);
        return o;
    }

    private static LitemallOrderGoods line(int id, int orderId, Integer comment) {
        LitemallOrderGoods g = new LitemallOrderGoods();
        g.setId(id);
        g.setOrderId(orderId);
        g.setGoodsId(77);
        g.setComment(comment);
        return g;
    }

    @Test
    void ordersQueryBindsCallerReceivedStatusesAndLiteralNotDeleted() {
        when(orderMapper.selectByExampleSelective(any(), any())).thenReturn(List.of());

        PurchaseVerificationService.Eligibility e = service.findEligibleLine(5, 77, null);

        assertFalse(e.eligible());
        assertEquals(PurchaseVerificationService.Refusal.NOT_PURCHASED, e.refusal());
        ArgumentCaptor<LitemallOrderExample> cap = ArgumentCaptor.forClass(LitemallOrderExample.class);
        verify(orderMapper).selectByExampleSelective(cap.capture(), eq(LitemallOrder.Column.id));
        List<LitemallOrderExample.Criterion> crit = cap.getValue().getOredCriteria().get(0).getAllCriteria();
        assertTrue(crit.stream().anyMatch(c -> "user_id =".equals(c.getCondition()) && Integer.valueOf(5).equals(c.getValue())));
        assertTrue(crit.stream().anyMatch(c -> "order_status in".equals(c.getCondition())
                && List.of((short) 401, (short) 402).equals(c.getValue())));
        // The literal binding — andLogicalDeleted() is inverted across the domain and must not be used.
        assertTrue(crit.stream().anyMatch(c -> "deleted =".equals(c.getCondition()) && Boolean.FALSE.equals(c.getValue())));
        assertTrue(crit.stream().noneMatch(c -> c.getCondition().startsWith("id ")), "no order pin when orderId is null");
        verify(orderGoodsMapper, never()).selectByExample(any());
    }

    @Test
    void orderIdPinsTheOrdersQueryToThatOrder() {
        when(orderMapper.selectByExampleSelective(any(), any())).thenReturn(List.of());

        service.findEligibleLine(5, 77, 913);

        ArgumentCaptor<LitemallOrderExample> cap = ArgumentCaptor.forClass(LitemallOrderExample.class);
        verify(orderMapper).selectByExampleSelective(cap.capture(), any());
        List<LitemallOrderExample.Criterion> crit = cap.getValue().getOredCriteria().get(0).getAllCriteria();
        assertTrue(crit.stream().anyMatch(c -> "id =".equals(c.getCondition()) && Integer.valueOf(913).equals(c.getValue())));
        // Still the caller's, still delivered — the pin narrows, it never widens.
        assertTrue(crit.stream().anyMatch(c -> "user_id =".equals(c.getCondition())));
        assertTrue(crit.stream().anyMatch(c -> "order_status in".equals(c.getCondition())));
    }

    @Test
    void deliveredOrderWithoutTheGoodsIsNotPurchased() {
        when(orderMapper.selectByExampleSelective(any(), any())).thenReturn(List.of(order(10)));
        when(orderGoodsMapper.selectByExample(any())).thenReturn(List.of());

        PurchaseVerificationService.Eligibility e = service.findEligibleLine(5, 77, null);

        assertEquals(PurchaseVerificationService.Refusal.NOT_PURCHASED, e.refusal());
        ArgumentCaptor<LitemallOrderGoodsExample> cap = ArgumentCaptor.forClass(LitemallOrderGoodsExample.class);
        verify(orderGoodsMapper).selectByExample(cap.capture());
        List<LitemallOrderGoodsExample.Criterion> crit = cap.getValue().getOredCriteria().get(0).getAllCriteria();
        assertTrue(crit.stream().anyMatch(c -> "order_id in".equals(c.getCondition()) && List.of(10).equals(c.getValue())));
        assertTrue(crit.stream().anyMatch(c -> "goods_id =".equals(c.getCondition()) && Integer.valueOf(77).equals(c.getValue())));
        assertTrue(crit.stream().anyMatch(c -> "deleted =".equals(c.getCondition()) && Boolean.FALSE.equals(c.getValue())));
        assertEquals("add_time asc, id asc", cap.getValue().getOrderByClause());
    }

    @Test
    void oldestUnreviewedLineWinsAndNullCommentCountsAsUnreviewed() {
        when(orderMapper.selectByExampleSelective(any(), any())).thenReturn(List.of(order(10), order(11)));
        // Mapper returns in the requested order: the first line is already reviewed (comment id 500).
        when(orderGoodsMapper.selectByExample(any())).thenReturn(List.of(
                line(1, 10, 500), line(2, 11, null), line(3, 11, 0)));

        PurchaseVerificationService.Eligibility e = service.findEligibleLine(5, 77, null);

        assertTrue(e.eligible());
        assertEquals(2, e.line().getId());
        assertNull(e.refusal());
    }

    @Test
    void everyLineReviewedIsAlreadyReviewed() {
        when(orderMapper.selectByExampleSelective(any(), any())).thenReturn(List.of(order(10)));
        when(orderGoodsMapper.selectByExample(any())).thenReturn(List.of(line(1, 10, 500), line(2, 10, 501)));

        PurchaseVerificationService.Eligibility e = service.findEligibleLine(5, 77, null);

        assertFalse(e.eligible());
        assertEquals(PurchaseVerificationService.Refusal.ALREADY_REVIEWED, e.refusal());
    }

    @Test
    void nullIdentityOrGoodsNeverQueries() {
        assertEquals(PurchaseVerificationService.Refusal.NOT_PURCHASED, service.findEligibleLine(null, 77, null).refusal());
        assertEquals(PurchaseVerificationService.Refusal.NOT_PURCHASED, service.findEligibleLine(5, null, null).refusal());
        verify(orderMapper, never()).selectByExampleSelective(any(), any());
    }

    @Test
    void markReviewedIsACompareAndSetOnCommentZeroOrNull() {
        when(orderGoodsMapper.updateByExampleSelective(any(), any())).thenReturn(1);

        assertTrue(service.markReviewed(42, 900));

        ArgumentCaptor<LitemallOrderGoods> row = ArgumentCaptor.forClass(LitemallOrderGoods.class);
        ArgumentCaptor<LitemallOrderGoodsExample> ex = ArgumentCaptor.forClass(LitemallOrderGoodsExample.class);
        verify(orderGoodsMapper).updateByExampleSelective(row.capture(), ex.capture());
        assertEquals(900, row.getValue().getComment());
        assertNull(row.getValue().getGoodsId(), "selective patch touches comment + update_time only");
        List<LitemallOrderGoodsExample.Criteria> ored = ex.getValue().getOredCriteria();
        assertEquals(2, ored.size(), "id AND comment=0, OR id AND comment IS NULL");
        for (LitemallOrderGoodsExample.Criteria c : ored) {
            assertTrue(c.getAllCriteria().stream().anyMatch(k -> "id =".equals(k.getCondition()) && Integer.valueOf(42).equals(k.getValue())));
        }
        assertTrue(ored.get(0).getAllCriteria().stream().anyMatch(k -> "comment =".equals(k.getCondition().replace("`", "")) && Integer.valueOf(0).equals(k.getValue())));
        assertTrue(ored.get(1).getAllCriteria().stream().anyMatch(k -> "comment is null".equals(k.getCondition().replace("`", ""))));
    }

    @Test
    void markReviewedReportsALostRaceAndRefusesJunkIds() {
        when(orderGoodsMapper.updateByExampleSelective(any(), any())).thenReturn(0);
        assertFalse(service.markReviewed(42, 900));

        assertFalse(service.markReviewed(null, 900));
        assertFalse(service.markReviewed(42, null));
        assertFalse(service.markReviewed(42, 0));
        verify(orderGoodsMapper, Mockito.times(1)).updateByExampleSelective(any(), any());
    }
}
