package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.order.domain.events.order.LitemallOrderPaidEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.infrastructure.configuration.FulfillmentProperties;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.ReceiptPrinterPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.fulfillment.ReceiptPrintJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Auto-print a receipt after every successful payment (Wave 4, Task D).
 *
 * <p>Wiring: the pay path ({@code LitemallOrderServiceImpl.markOrderPaid}, inside the
 * orchestrator's transaction) publishes {@link LitemallOrderPaidEvent} through
 * {@code LitemallSpringDomainEventPublisher} → Spring's {@code ApplicationEventPublisher},
 * so {@code @TransactionalEventListener(AFTER_COMMIT)} fires exactly when the payment
 * COMMITS — never for a rolled-back payment ({@code fallbackExecution=false} keeps it
 * silent outside a transaction). The event fires at most once per order thanks to the pay
 * path's guarded 0-row UPDATE (a concurrent/retried PAY aborts before publishing), so
 * {@code originId = orderSn} gives vendor-side exactly-once printing on top — Yly dedupes
 * on origin_id (this fixes crmeb's hardcoded {@code "order111"} origin_id, which collapses
 * every order into one dedupe key).
 *
 * <p>PAYMENT MUST NEVER BLOCK OR FAIL BECAUSE OF PRINTING: the listener only enqueues onto
 * a dedicated single-thread executor (bounded queue of 100, discard-oldest — under a
 * printer outage the newest receipts win; a lost receipt is reprintable from the admin
 * surface) and the worker catch-alls every throwable into a WARN.
 */
@Component
public class OrderPaidReceiptPrintListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidReceiptPrintListener.class);

    private final LitemallOrderRepository orderRepository;
    private final LitemallOrderGoodsRepository orderGoodsRepository;
    private final ReceiptPrinterPort receiptPrinterPort;
    private final FulfillmentProperties properties;

    /** 1 daemon worker, bounded queue, discard-oldest: printing never backs up payments. */
    private final ThreadPoolExecutor executor;

    public OrderPaidReceiptPrintListener(LitemallOrderRepository orderRepository,
                                         LitemallOrderGoodsRepository orderGoodsRepository,
                                         ReceiptPrinterPort receiptPrinterPort,
                                         FulfillmentProperties properties) {
        this.orderRepository = orderRepository;
        this.orderGoodsRepository = orderGoodsRepository;
        this.receiptPrinterPort = receiptPrinterPort;
        this.properties = properties;
        AtomicInteger counter = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                runnable -> {
                    Thread thread = new Thread(runnable, "receipt-print-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onOrderPaid(LitemallOrderPaidEvent event) {
        if (!properties.getPrinter().isAutoPrint()) {
            return;
        }
        try {
            executor.execute(() -> printReceipt(event));
        } catch (RuntimeException e) {
            // Even a rejected submit must never surface to the (already committed) payment.
            log.warn("Receipt print enqueue failed for order {}: {}",
                    event.getOrderId().getId(), e.getMessage());
        }
    }

    private void printReceipt(LitemallOrderPaidEvent event) {
        try {
            LitemallOrderAggregate order = orderRepository.findById(event.getOrderId()).orElse(null);
            if (order == null) {
                log.warn("Receipt print skipped: order {} not found after commit",
                        event.getOrderId().getId());
                return;
            }
            // Vendor-side exactly-once key: the auto-print of one order is always its
            // orderSn (admin reprints suffix -R<ts> to bypass the dedupe).
            ReceiptPrintJob job = toJob(order, orderGoodsRepository.findByOId(order.getOrderId()),
                    properties.getPrinter().getBusinessName(), order.getOrderSn());
            ReceiptPrinterPort.PrintOutcome outcome = receiptPrinterPort.print(job);
            if (outcome != ReceiptPrinterPort.PrintOutcome.OK) {
                log.warn("Receipt auto-print FAILED for order {} (originId {}) — reprintable "
                        + "via POST /srv/private/admin/order/{}/print-receipt",
                        order.getOrderId().getId(), order.getOrderSn(), order.getOrderId().getId());
            }
        } catch (Throwable t) {
            // Catch-ALL: a printing bug must never kill the single worker thread.
            log.warn("Receipt auto-print crashed for order {}: {}",
                    event.getOrderId().getId(), t.toString());
        }
    }

    /**
     * Aggregate → print job. Shared with the admin reprint endpoint so both surfaces
     * print the identical receipt (only the {@code originId} differs).
     */
    public static ReceiptPrintJob toJob(LitemallOrderAggregate order,
                                        List<LitemallOrderGoodsAggregate> orderGoods,
                                        String businessName, String originId) {
        List<ReceiptPrintJob.Line> lines = new ArrayList<>();
        for (LitemallOrderGoodsAggregate goods : orderGoods) {
            lines.add(new ReceiptPrintJob.Line(goods.getGoodsName(),
                    goods.getNumber() == null ? 0 : goods.getNumber(), amount(goods.getPrice())));
        }
        return ReceiptPrintJob.builder()
                .originId(originId)
                .orderSn(order.getOrderSn())
                .businessName(businessName)
                .deliveryType(order.getDeliveryType())
                .verifyCode(order.getVerifyCode())
                .consignee(order.getConsignee())
                .mobile(order.getMobile())
                .addTime(order.getAddTime())
                .payTime(order.getPayTime())
                .lines(lines)
                .goodsPrice(amount(order.getGoodsPrice()))
                .freightPrice(amount(order.getFreightPrice()))
                .couponPrice(amount(order.getCouponPrice()))
                .actualPrice(amount(order.getActualPrice()))
                .build();
    }

    private static BigDecimal amount(LitemallMoney money) {
        return money == null ? null : money.getAmount();
    }
}
