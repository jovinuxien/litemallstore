package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.core.mail.CustomerMailProperties;
import org.linlinjava.litemall.core.mail.MailTemplates;
import org.linlinjava.litemall.db.dao.LitemallUserMapper;
import org.linlinjava.litemall.db.dao.MailOutboxMapper;
import org.linlinjava.litemall.db.domain.LitemallMailOutbox;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderPaidEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Wave-10 coverage for the paid → order-confirmation enqueue path: rich body
 * (items + amounts + delivery), blank-email skip, goods-load failure fallback
 * to the minimal body, pickup double-mail and the disabled default.
 *
 * <p>The listener hands work to its single daemon worker, so outcomes are
 * asserted with {@code verify(..., timeout(...))} / {@code after(...)}.
 */
class CustomerMailEnqueueListenerTest {

    private static final int VERIFY_TIMEOUT_MS = 5000;

    private final LitemallOrderRepository orderRepository = mock(LitemallOrderRepository.class);
    private final LitemallOrderGoodsRepository orderGoodsRepository = mock(LitemallOrderGoodsRepository.class);
    private final LitemallUserMapper userMapper = mock(LitemallUserMapper.class);
    private final MailOutboxMapper mailOutboxMapper = mock(MailOutboxMapper.class);

    private CustomerMailEnqueueListener listener(boolean enabled) {
        return listener(enabled, ""); // Wave-23 admin notify OFF (the blank-env default)
    }

    private CustomerMailEnqueueListener listener(boolean enabled, String adminNotifyEmail) {
        CustomerMailProperties properties = new CustomerMailProperties();
        properties.setEnabled(enabled);
        return new CustomerMailEnqueueListener(orderRepository, orderGoodsRepository,
                userMapper, mailOutboxMapper, properties, adminNotifyEmail, 30);
    }

    private static LitemallOrderAggregate order(int orderId) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(new LitemallOrderId(orderId));
        order.setUserId(new LitemallUserId(7));
        order.setOrderSn("20260726000042");
        order.setDeliveryType(LitemallOrderAggregate.DELIVERY_EXPRESS);
        order.setConsignee("Jane Buyer");
        order.setMobile("+1 555 0100");
        order.setAddress("1 Main St, Springfield, IL 62701, US");
        order.setGoodsPrice(money("39.96"));
        order.setFreightPrice(money("5.00"));
        order.setCouponPrice(money("2.00"));
        order.setTaxPrice(money("1.20"));
        order.setActualPrice(money("44.16"));
        order.setPayTime(LocalDateTime.of(2026, 7, 26, 14, 3));
        return order;
    }

    private static LitemallOrderGoodsAggregate line(String name, String[] specs, int number, String price) {
        LitemallOrderGoodsAggregate goods = new LitemallOrderGoodsAggregate();
        goods.setGoodsName(name);
        goods.setSpecifications(specs);
        goods.setNumber((short) number);
        goods.setPrice(money(price));
        return goods;
    }

    private static LitemallMoney money(String amount) {
        return new LitemallMoney(new BigDecimal(amount));
    }

    private void stubBuyerEmail(String email) {
        LitemallUser user = new LitemallUser();
        user.setEmail(email);
        when(userMapper.selectByPrimaryKey(7)).thenReturn(user);
    }

    @Test
    void paidEvent_enqueuesRichConfirmation() {
        LitemallOrderAggregate order = order(42);
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(orderGoodsRepository.findByOId(any())).thenReturn(List.of(
                line("Wireless Mouse", new String[]{"Black", "USB-C"}, 2, "9.99"),
                line("Desk Mat", null, 1, "19.98")));
        stubBuyerEmail("buyer@example.com");

        listener(true).onOrderPaid(new LitemallOrderPaidEvent(new LitemallOrderId(42)));

        ArgumentCaptor<LitemallMailOutbox> captor = ArgumentCaptor.forClass(LitemallMailOutbox.class);
        verify(mailOutboxMapper, timeout(VERIFY_TIMEOUT_MS)).insert(captor.capture());
        LitemallMailOutbox row = captor.getValue();
        assertThat(row.getRecipient()).isEqualTo("buyer@example.com");
        assertThat(row.getTemplateKey()).isEqualTo(MailTemplates.KEY_ORDER_CONFIRMATION);
        assertThat(row.getStatus()).isEqualTo(LitemallMailOutbox.STATUS_PENDING);
        assertThat(row.getSendAt()).isNotNull();
        assertThat(row.getSubject()).contains("20260726000042");
        assertThat(row.getBody())
                .contains("Wireless Mouse (Black, USB-C) x 2 — €9.99")
                .contains("Desk Mat x 1 — €19.98")
                .contains("Paid at: 2026-07-26 14:03")
                .contains("Items subtotal:")
                .contains("€39.96")
                .contains("Shipping:")
                .contains("€5.00")
                .contains("Coupon discount:")
                .contains("-€2.00")
                .contains("Tax:")
                .contains("€1.20")
                .contains("Order total:")
                .contains("€44.16")
                .contains("Consignee: Jane Buyer")
                .contains("Phone: +1 555 0100")
                .contains("Address: 1 Main St, Springfield, IL 62701, US");
    }

    /**
     * The store charges EUR storewide since Wave 24 (2026-08-09), so no mail may render a
     * dollar amount. The two SPAs were swept then; these bodies were not, and a customer
     * charged EUR was reading "$" in the confirmation. Pins BOTH mails this event raises.
     */
    /** D1 of the lifecycle plan: the stray-payment refund tells the customer, with the amount Stripe reversed. */
    @Test
    void strayPaymentRefunded_enqueuesThePaymentRefundedMail_withTheEventAmount() {
        LitemallOrderAggregate order = order(42);
        order.setActualPrice(money("99.99")); // deliberately NOT the refunded amount
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        stubBuyerEmail("buyer@example.com");

        listener(true).onStrayPaymentRefunded(
                new org.linlinjava.litemall.order.domain.events.payment.LitemallStrayPaymentRefundedEvent(
                        new LitemallOrderId(42), "pi_late", new BigDecimal("8.58"), "re_9", "SYSTEM_CANCELED"));

        ArgumentCaptor<LitemallMailOutbox> captor = ArgumentCaptor.forClass(LitemallMailOutbox.class);
        verify(mailOutboxMapper, timeout(VERIFY_TIMEOUT_MS)).insert(captor.capture());
        LitemallMailOutbox row = captor.getValue();
        assertThat(row.getRecipient()).isEqualTo("buyer@example.com");
        assertThat(row.getTemplateKey()).isEqualTo(MailTemplates.KEY_PAYMENT_REFUNDED);
        assertThat(row.getSubject()).contains("20260726000042").contains("refunded");
        assertThat(row.getBody()).contains("\u20ac8.58").doesNotContain("99.99");
        assertThat(row.getBodyHtml()).contains("\u20ac8.58").contains("Nothing will be shipped");
    }

    /** F14: the order closed — the customer learns the date the return window started from. */
    @Test
    void deliveredEvent_enqueuesTheDeliveredMail_withTheReturnWindow() {
        LitemallOrderAggregate order = order(42);
        order.setConfirmTime(LocalDateTime.of(2026, 9, 5, 10, 0));
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        stubBuyerEmail("buyer@example.com");

        listener(true).onOrderDelivered(
                new org.linlinjava.litemall.order.domain.events.order.LitemallOrderDeliveredEvent(
                        new LitemallOrderId(42), true));

        ArgumentCaptor<LitemallMailOutbox> captor = ArgumentCaptor.forClass(LitemallMailOutbox.class);
        verify(mailOutboxMapper, timeout(VERIFY_TIMEOUT_MS)).insert(captor.capture());
        LitemallMailOutbox row = captor.getValue();
        assertThat(row.getTemplateKey()).isEqualTo(MailTemplates.KEY_DELIVERED);
        assertThat(row.getSubject()).contains("delivered");
        assertThat(row.getBody()).contains("2026-09-05").contains("30 days");
        assertThat(row.getBodyHtml()).contains("30 days");
    }

    /** D2: CJ cancelled a paid order — the customer is told support will contact them. */
    @Test
    void cjFulfilmentCancelled_enqueuesTheFulfilmentCancelledMail() {
        when(orderRepository.findById(any())).thenReturn(Optional.of(order(42)));
        stubBuyerEmail("buyer@example.com");

        listener(true).onCjFulfilmentCancelled(
                new org.linlinjava.litemall.order.domain.events.cj.LitemallCjFulfilmentCancelledEvent(
                        new LitemallOrderId(42), "cj-42"));

        ArgumentCaptor<LitemallMailOutbox> captor = ArgumentCaptor.forClass(LitemallMailOutbox.class);
        verify(mailOutboxMapper, timeout(VERIFY_TIMEOUT_MS)).insert(captor.capture());
        LitemallMailOutbox row = captor.getValue();
        assertThat(row.getTemplateKey()).isEqualTo(MailTemplates.KEY_FULFILMENT_CANCELLED);
        assertThat(row.getSubject()).contains("20260726000042").contains("could not fulfil");
        assertThat(row.getBody()).contains("will contact you").doesNotContain("refund has been issued");
        assertThat(row.getBodyHtml()).contains("could not fulfil");
    }

    @Test
    void paidMails_renderStoreCurrency_neverDollars() {
        LitemallOrderAggregate order = order(42);
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(orderGoodsRepository.findByOId(any())).thenReturn(List.of(
                line("Wireless Mouse", new String[]{"Black", "USB-C"}, 2, "9.99")));
        stubBuyerEmail("buyer@example.com");

        listener(true, "contact@trovemo.com")
                .onOrderPaid(new LitemallOrderPaidEvent(new LitemallOrderId(42)));

        ArgumentCaptor<LitemallMailOutbox> captor = ArgumentCaptor.forClass(LitemallMailOutbox.class);
        verify(mailOutboxMapper, timeout(VERIFY_TIMEOUT_MS).times(2)).insert(captor.capture());
        for (LitemallMailOutbox row : captor.getAllValues()) {
            assertThat(row.getSubject()).doesNotContain("$");
            assertThat(row.getBody()).doesNotContain("$");
            assertThat(row.getBody()).contains("\u20ac44.16");
        }
    }

    @Test
    void paidEvent_zeroCouponAndTaxLinesOmitted() {
        LitemallOrderAggregate order = order(42);
        order.setCouponPrice(money("0.00"));
        order.setTaxPrice(money("0.00"));
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(orderGoodsRepository.findByOId(any())).thenReturn(List.of(
                line("Wireless Mouse", null, 1, "9.99")));
        stubBuyerEmail("buyer@example.com");

        listener(true).onOrderPaid(new LitemallOrderPaidEvent(new LitemallOrderId(42)));

        ArgumentCaptor<LitemallMailOutbox> captor = ArgumentCaptor.forClass(LitemallMailOutbox.class);
        verify(mailOutboxMapper, timeout(VERIFY_TIMEOUT_MS)).insert(captor.capture());
        assertThat(captor.getValue().getBody())
                .doesNotContain("Coupon discount:")
                .doesNotContain("Tax:")
                .contains("Order total:");
    }

    @Test
    void blankEmailUser_noRowNoError() {
        when(orderRepository.findById(any())).thenReturn(Optional.of(order(42)));
        stubBuyerEmail("  ");

        listener(true).onOrderPaid(new LitemallOrderPaidEvent(new LitemallOrderId(42)));

        verify(mailOutboxMapper, after(500).never()).insert(any());
    }

    @Test
    void goodsLoadFailure_fallsBackToMinimalBody() {
        when(orderRepository.findById(any())).thenReturn(Optional.of(order(42)));
        when(orderGoodsRepository.findByOId(any())).thenThrow(new RuntimeException("db down"));
        stubBuyerEmail("buyer@example.com");

        listener(true).onOrderPaid(new LitemallOrderPaidEvent(new LitemallOrderId(42)));

        ArgumentCaptor<LitemallMailOutbox> captor = ArgumentCaptor.forClass(LitemallMailOutbox.class);
        verify(mailOutboxMapper, timeout(VERIFY_TIMEOUT_MS)).insert(captor.capture());
        LitemallMailOutbox row = captor.getValue();
        assertThat(row.getTemplateKey()).isEqualTo(MailTemplates.KEY_ORDER_CONFIRMATION);
        assertThat(row.getBody())
                .contains("Order total: €44.16")
                .contains("20260726000042")
                .doesNotContain("Your items");
    }

    @Test
    void pickupOrder_confirmationShowsPickupAndCodeMailStillSent() {
        LitemallOrderAggregate order = order(42);
        order.setDeliveryType(LitemallOrderAggregate.DELIVERY_PICKUP);
        order.setAddress("PICKUP: Springfield Store");
        order.setVerifyCode("ABX123");
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(orderGoodsRepository.findByOId(any())).thenReturn(List.of(
                line("Wireless Mouse", null, 1, "9.99")));
        stubBuyerEmail("buyer@example.com");

        listener(true).onOrderPaid(new LitemallOrderPaidEvent(new LitemallOrderId(42)));

        ArgumentCaptor<LitemallMailOutbox> captor = ArgumentCaptor.forClass(LitemallMailOutbox.class);
        verify(mailOutboxMapper, timeout(VERIFY_TIMEOUT_MS).times(2)).insert(captor.capture());
        List<LitemallMailOutbox> rows = captor.getAllValues();
        LitemallMailOutbox confirmation = rows.stream()
                .filter(r -> MailTemplates.KEY_ORDER_CONFIRMATION.equals(r.getTemplateKey()))
                .findFirst().orElseThrow();
        LitemallMailOutbox pickup = rows.stream()
                .filter(r -> MailTemplates.KEY_PICKUP_CODE.equals(r.getTemplateKey()))
                .findFirst().orElseThrow();
        assertThat(confirmation.getBody())
                .contains("Pickup at: Springfield Store")
                .doesNotContain("Consignee:");
        assertThat(pickup.getBody()).contains("ABX123");
    }

    @Test
    void disabled_writesNothingAndReadsNothing() {
        listener(false).onOrderPaid(new LitemallOrderPaidEvent(new LitemallOrderId(42)));

        verify(mailOutboxMapper, after(500).never()).insert(any());
        verifyZeroInteractions(orderRepository, orderGoodsRepository, userMapper);
    }

    // ------------------------------------------------------------------
    // Wave 23: admin order-paid notification
    // ------------------------------------------------------------------

    @Test
    void adminNotify_configured_enqueuesNoticeWithContractSubjectAndSummary() {
        LitemallOrderAggregate order = order(42);
        order.setCountryCode("US");
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(orderGoodsRepository.findByOId(any())).thenReturn(List.of(
                line("Wireless Mouse", new String[]{"Black"}, 2, "9.99")));
        stubBuyerEmail("buyer@example.com");

        listener(true, "contact@trovemo.com")
                .onOrderPaid(new LitemallOrderPaidEvent(new LitemallOrderId(42)));

        ArgumentCaptor<LitemallMailOutbox> captor = ArgumentCaptor.forClass(LitemallMailOutbox.class);
        verify(mailOutboxMapper, timeout(VERIFY_TIMEOUT_MS).times(2)).insert(captor.capture());
        LitemallMailOutbox notice = captor.getAllValues().stream()
                .filter(r -> "admin_order_paid".equals(r.getTemplateKey()))
                .findFirst().orElseThrow();
        assertThat(notice.getRecipient()).isEqualTo("contact@trovemo.com");
        assertThat(notice.getSubject()).isEqualTo("New paid order 20260726000042 — €44.16");
        assertThat(notice.getBody())
                .contains("Wireless Mouse")
                .contains("x2 @ €9.99")
                .contains("Buyer country: US")
                .contains("Total: €44.16")
                .contains("Approve it for fulfilment in the admin panel");
    }

    /** The admin notice is NOT gated on the buyer having an email — customer skip, admin row. */
    @Test
    void adminNotify_emailLessBuyer_stillNotifiesAdmin() {
        LitemallOrderAggregate order = order(42);
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(orderGoodsRepository.findByOId(any())).thenReturn(List.of(
                line("Wireless Mouse", null, 1, "9.99")));
        stubBuyerEmail(null);

        listener(true, "contact@trovemo.com")
                .onOrderPaid(new LitemallOrderPaidEvent(new LitemallOrderId(42)));

        ArgumentCaptor<LitemallMailOutbox> captor = ArgumentCaptor.forClass(LitemallMailOutbox.class);
        verify(mailOutboxMapper, timeout(VERIFY_TIMEOUT_MS)).insert(captor.capture());
        assertThat(captor.getValue().getTemplateKey()).isEqualTo("admin_order_paid");
        verify(mailOutboxMapper, after(500).times(1)).insert(any());
    }

    @Test
    void adminNotify_blankEnv_zeroAdminRows() {
        LitemallOrderAggregate order = order(42);
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(orderGoodsRepository.findByOId(any())).thenReturn(List.of(
                line("Wireless Mouse", null, 1, "9.99")));
        stubBuyerEmail("buyer@example.com");

        listener(true, "  ").onOrderPaid(new LitemallOrderPaidEvent(new LitemallOrderId(42)));

        // exactly the customer confirmation — no admin row
        ArgumentCaptor<LitemallMailOutbox> captor = ArgumentCaptor.forClass(LitemallMailOutbox.class);
        verify(mailOutboxMapper, timeout(VERIFY_TIMEOUT_MS)).insert(captor.capture());
        verify(mailOutboxMapper, after(500).times(1)).insert(any());
        assertThat(captor.getValue().getTemplateKey()).isEqualTo(MailTemplates.KEY_ORDER_CONFIRMATION);
    }

    @Test
    void adminNotify_disabledMailFlag_writesNothing() {
        listener(false, "contact@trovemo.com")
                .onOrderPaid(new LitemallOrderPaidEvent(new LitemallOrderId(42)));

        verify(mailOutboxMapper, after(500).never()).insert(any());
        verifyZeroInteractions(orderRepository, orderGoodsRepository, userMapper);
    }
}
