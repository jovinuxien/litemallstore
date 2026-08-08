package org.linlinjava.litemall.order.application.internal.cj;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CjPlacementApprovalService} (Wave 23, V59): pending list assembles an honest
 * per-order readiness verdict from LOCAL reads only, and approve is an idempotent CAS
 * stamp with typed refusals — it never places anything itself.
 */
@ExtendWith(MockitoExtension.class)
class CjPlacementApprovalServiceTest {

    private static final LitemallOrderId ORDER_ID = new LitemallOrderId(88);

    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private LitemallOrderGoodsRepository orderGoodsRepository;
    @Mock
    private LitemallOrderStatusHistoryRepository statusHistoryRepository;
    @Mock
    private CjOrderLineResolver lineResolver;

    private CjPlacementApprovalService service(int iossType) {
        return new CjPlacementApprovalService(orderRepository, orderGoodsRepository,
                statusHistoryRepository, lineResolver, iossType);
    }

    private LitemallOrderAggregate paidUnplacedCjOrder() {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(ORDER_ID);
        order.setUserId(new LitemallUserId(42));
        order.setOrderSn("20260808000088");
        order.setOrderStatus(LitemallOrderStatus.PAID);
        order.setSource(LitemallOrderAggregate.SOURCE_CJ);
        order.setActualPrice(new LitemallMoney(new BigDecimal("25.00")));
        order.setCountryCode("US");
        return order;
    }

    private LitemallOrderGoodsAggregate line(int productId, String name) {
        LitemallOrderGoodsAggregate g = new LitemallOrderGoodsAggregate();
        g.setProductId(new LitemallGoodsProductId(productId));
        g.setGoodsName(name);
        g.setNumber((short) 1);
        g.setPrice(new LitemallMoney(new BigDecimal("10.00")));
        return g;
    }

    // ------------------------------------------------------------------
    // Pending list / readiness assessment
    // ------------------------------------------------------------------

    @Test
    void pending_resolvableLines_cjReady_noHold() {
        LitemallOrderAggregate order = paidUnplacedCjOrder();
        when(orderRepository.queryCjPlacementPending(1, 10)).thenReturn(List.of(order));
        when(orderRepository.countCjPlacementPending()).thenReturn(1L);
        when(orderGoodsRepository.findByOId(ORDER_ID)).thenReturn(List.of(line(5, "Mug")));
        when(lineResolver.resolveVid(5)).thenReturn("cj-vid-5");

        CjPlacementApprovalService.PendingPage page = service(0).pending(1, 10);

        assertEquals(1L, page.total());
        CjPlacementApprovalService.PendingOrder row = page.list().get(0);
        assertTrue(row.cjReady());
        assertNull(row.holdReason());
    }

    @Test
    void pending_unresolvableVariant_notReady_namedInHold() {
        LitemallOrderAggregate order = paidUnplacedCjOrder();
        when(orderRepository.queryCjPlacementPending(1, 10)).thenReturn(List.of(order));
        when(orderRepository.countCjPlacementPending()).thenReturn(1L);
        when(orderGoodsRepository.findByOId(ORDER_ID))
                .thenReturn(List.of(line(5, "Mug"), line(6, "Lamp")));
        when(lineResolver.resolveVid(5)).thenReturn("cj-vid-5");
        when(lineResolver.resolveVid(6)).thenThrow(new LitemallCjOrderException("no cj_vid"));

        CjPlacementApprovalService.PendingOrder row = service(0).pending(1, 10).list().get(0);

        assertFalse(row.cjReady());
        assertNotNull(row.holdReason());
        assertTrue(row.holdReason().contains("Lamp"));
        assertFalse(row.holdReason().contains("Mug,"));
    }

    @Test
    void pending_placementRejectedSentinel_notReady_holdExplainsRequeue() {
        LitemallOrderAggregate order = paidUnplacedCjOrder();
        order.setCjOrderStatus(CjPlacementService.STATUS_PLACEMENT_REJECTED);
        when(orderRepository.queryCjPlacementPending(1, 10)).thenReturn(List.of(order));
        when(orderRepository.countCjPlacementPending()).thenReturn(1L);
        when(orderGoodsRepository.findByOId(ORDER_ID)).thenReturn(List.of());

        CjPlacementApprovalService.PendingOrder row = service(0).pending(1, 10).list().get(0);

        assertFalse(row.cjReady());
        assertTrue(row.holdReason().contains("PLACEMENT_REJECTED"));
    }

    @Test
    void pending_openAftersale_holdsWithoutBlockingReadiness() {
        LitemallOrderAggregate order = paidUnplacedCjOrder();
        order.setAfterSaleStatus(LitemallAfterSaleStatus.STATUS_REQUEST);
        when(orderRepository.queryCjPlacementPending(1, 10)).thenReturn(List.of(order));
        when(orderRepository.countCjPlacementPending()).thenReturn(1L);
        when(orderGoodsRepository.findByOId(ORDER_ID)).thenReturn(List.of(line(5, "Mug")));
        when(lineResolver.resolveVid(5)).thenReturn("cj-vid-5");

        CjPlacementApprovalService.PendingOrder row = service(0).pending(1, 10).list().get(0);

        assertTrue(row.cjReady());
        assertTrue(row.holdReason().contains("aftersale"));
    }

    @Test
    void pending_euDestination_iossUnconfigured_informationalHold() {
        LitemallOrderAggregate order = paidUnplacedCjOrder();
        order.setCountryCode("DE");
        when(orderRepository.queryCjPlacementPending(1, 10)).thenReturn(List.of(order));
        when(orderRepository.countCjPlacementPending()).thenReturn(1L);
        when(orderGoodsRepository.findByOId(ORDER_ID)).thenReturn(List.of(line(5, "Mug")));
        when(lineResolver.resolveVid(5)).thenReturn("cj-vid-5");

        assertTrue(service(0).pending(1, 10).list().get(0).holdReason().contains("IOSS"));
        // With IOSS explicitly configured the note disappears.
        assertNull(service(2).pending(1, 10).list().get(0).holdReason());
    }

    // ------------------------------------------------------------------
    // Approve
    // ------------------------------------------------------------------

    @Test
    void approve_paidUnplacedCjOrder_stampsWithAdminId_recordsHop() {
        LitemallOrderAggregate before = paidUnplacedCjOrder();
        LitemallOrderAggregate after = paidUnplacedCjOrder();
        after.setCjPlacementApprovedTime(LocalDateTime.now());
        after.setCjPlacementApprovedBy("7");
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(before))
                .thenReturn(Optional.of(after));
        when(orderRepository.stampCjPlacementApproval(ORDER_ID, "7")).thenReturn(1);

        CjPlacementApprovalService.ApproveResult result = service(0).approve(88, "7");

        assertEquals(CjPlacementApprovalService.ApproveStatus.APPROVED, result.status());
        assertEquals("7", result.approvedBy());
        assertNotNull(result.approvedTime());
        ArgumentCaptor<LitemallOrderStatusChange> hop =
                ArgumentCaptor.forClass(LitemallOrderStatusChange.class);
        verify(statusHistoryRepository).record(hop.capture());
        assertTrue(hop.getValue().getChangeMessage().contains("Approved for CJ fulfilment"));
    }

    @Test
    void approve_repeat_isIdempotent_returnsExistingStamp_noSecondWrite() {
        LitemallOrderAggregate order = paidUnplacedCjOrder();
        LocalDateTime stamp = LocalDateTime.now().minusMinutes(5);
        order.setCjPlacementApprovedTime(stamp);
        order.setCjPlacementApprovedBy("3");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        CjPlacementApprovalService.ApproveResult result = service(0).approve(88, "7");

        assertEquals(CjPlacementApprovalService.ApproveStatus.ALREADY_APPROVED, result.status());
        assertEquals(stamp, result.approvedTime());
        assertEquals("3", result.approvedBy());
        verify(orderRepository, never()).stampCjPlacementApproval(any(), anyString());
        verify(statusHistoryRepository, never()).record(any());
    }

    @Test
    void approve_lostCasRace_answersFromTheRow_asAlreadyApproved() {
        LitemallOrderAggregate before = paidUnplacedCjOrder();
        LitemallOrderAggregate winner = paidUnplacedCjOrder();
        winner.setCjPlacementApprovedTime(LocalDateTime.now());
        winner.setCjPlacementApprovedBy("9");
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(before))
                .thenReturn(Optional.of(winner));
        when(orderRepository.stampCjPlacementApproval(ORDER_ID, "7")).thenReturn(0);

        CjPlacementApprovalService.ApproveResult result = service(0).approve(88, "7");

        assertEquals(CjPlacementApprovalService.ApproveStatus.ALREADY_APPROVED, result.status());
        assertEquals("9", result.approvedBy());
    }

    @Test
    void approve_typedRefusals_notFound_notCj_notPaid_alreadyPlaced() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());
        assertEquals(CjPlacementApprovalService.ApproveStatus.NOT_FOUND,
                service(0).approve(88, "7").status());

        LitemallOrderAggregate local = paidUnplacedCjOrder();
        local.setSource("mall");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(local));
        assertEquals(CjPlacementApprovalService.ApproveStatus.NOT_CJ,
                service(0).approve(88, "7").status());

        LitemallOrderAggregate unpaid = paidUnplacedCjOrder();
        unpaid.setOrderStatus(LitemallOrderStatus.CREATED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(unpaid));
        assertEquals(CjPlacementApprovalService.ApproveStatus.NOT_PAID,
                service(0).approve(88, "7").status());

        LitemallOrderAggregate refunded = paidUnplacedCjOrder();
        refunded.setOrderStatus(LitemallOrderStatus.REFUND_REQUEST);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(refunded));
        assertEquals(CjPlacementApprovalService.ApproveStatus.NOT_PAID,
                service(0).approve(88, "7").status());

        LitemallOrderAggregate placed = paidUnplacedCjOrder();
        placed.setCjOrderId("cj-1");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(placed));
        assertEquals(CjPlacementApprovalService.ApproveStatus.ALREADY_PLACED,
                service(0).approve(88, "7").status());

        verify(orderRepository, never()).stampCjPlacementApproval(any(), anyString());
    }

    @Test
    void approve_blankAdminId_fallsBackToAdminLiteral() {
        LitemallOrderAggregate before = paidUnplacedCjOrder();
        LitemallOrderAggregate after = paidUnplacedCjOrder();
        after.setCjPlacementApprovedTime(LocalDateTime.now());
        after.setCjPlacementApprovedBy("admin");
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(before))
                .thenReturn(Optional.of(after));
        when(orderRepository.stampCjPlacementApproval(ORDER_ID, "admin")).thenReturn(1);

        CjPlacementApprovalService.ApproveResult result = service(0).approve(88, "  ");

        assertEquals(CjPlacementApprovalService.ApproveStatus.APPROVED, result.status());
        verify(orderRepository).stampCjPlacementApproval(ORDER_ID, "admin");
    }
}
