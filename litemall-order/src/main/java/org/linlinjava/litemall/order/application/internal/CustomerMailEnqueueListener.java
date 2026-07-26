package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.core.mail.CustomerMailProperties;
import org.linlinjava.litemall.core.mail.MailTemplates;
import org.linlinjava.litemall.db.dao.LitemallUserMapper;
import org.linlinjava.litemall.db.dao.MailOutboxMapper;
import org.linlinjava.litemall.db.domain.LitemallMailOutbox;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderPaidEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderRefundedEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderShippedEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wave-6 transactional customer email: turns committed order lifecycle events into
 * {@code litemall_mail_outbox} rows (paid → order-confirmation [+ pickup-code for
 * pickup orders], shipped → shipped, aftersale approved → refund-approved). The
 * {@link MailOutboxSweepScheduler} delivers the rows; this listener only renders
 * and enqueues.
 *
 * <p>Wiring mirrors {@link BrokerageService} / {@link OrderPaidReceiptPrintListener}
 * (the Wave-4/5 pattern): {@code @TransactionalEventListener(AFTER_COMMIT)} hands
 * off to a small dedicated executor and the worker catch-alls every throwable into
 * a WARN. PAYMENT (or shipping/refund) MUST NEVER BLOCK OR FAIL BECAUSE OF EMAIL.
 *
 * <p>Recipient is the buyer's {@code litemall_user.email} (V37, optional): NULL or
 * blank → silent skip, no row. With {@code litemall.customer-mail.enabled=false}
 * (the default) the listener writes nothing at all — byte-identical behavior.
 * Rows are enqueued with {@code send_at=now}; a future {@code send_at} is the
 * scheduled-send seam other writers can reuse.
 */
@Component
public class CustomerMailEnqueueListener {

    private static final Logger log = LoggerFactory.getLogger(CustomerMailEnqueueListener.class);

    private final LitemallOrderRepository orderRepository;
    private final LitemallOrderGoodsRepository orderGoodsRepository;
    private final LitemallUserMapper userMapper;
    private final MailOutboxMapper mailOutboxMapper;
    private final CustomerMailProperties mailProperties;

    /** 1 daemon worker, bounded queue: mail rendering never backs up payments. */
    private final ThreadPoolExecutor executor;

    public CustomerMailEnqueueListener(LitemallOrderRepository orderRepository,
                                       LitemallOrderGoodsRepository orderGoodsRepository,
                                       LitemallUserMapper userMapper,
                                       MailOutboxMapper mailOutboxMapper,
                                       CustomerMailProperties mailProperties) {
        this.orderRepository = orderRepository;
        this.orderGoodsRepository = orderGoodsRepository;
        this.userMapper = userMapper;
        this.mailOutboxMapper = mailOutboxMapper;
        this.mailProperties = mailProperties;
        AtomicInteger counter = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                runnable -> {
                    Thread thread = new Thread(runnable, "customer-mail-enqueue-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                // A dropped task is a lost email with no outbox row to resend: never
                // discard silently — WARN with enough context for a manual re-trigger.
                (runnable, pool) -> log.warn("Customer-mail enqueue queue full (size {}); dropping one task — "
                        + "check litemall_mail_outbox against recent order transitions", pool.getQueue().size()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onOrderPaid(LitemallOrderPaidEvent event) {
        submit(event.getOrderId().getId(), this::enqueuePaidMails);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onOrderShipped(LitemallOrderShippedEvent event) {
        submit(event.getOrderId().getId(), this::enqueueShippedMail);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onOrderRefunded(LitemallOrderRefundedEvent event) {
        submit(event.getOrderId().getId(), this::enqueueRefundApprovedMail);
    }

    private void submit(Integer orderId, java.util.function.BiConsumer<LitemallOrderAggregate, String> enqueue) {
        if (!mailProperties.isEnabled()) {
            return; // disabled default: no thread hop, no DB reads, no rows
        }
        try {
            executor.execute(() -> enqueueFor(orderId, enqueue));
        } catch (RuntimeException e) {
            // Even a rejected submit must never surface to the (already committed) transition.
            log.warn("Customer-mail enqueue submit failed for order {}: {}", orderId, e.getMessage());
        }
    }

    private void enqueueFor(Integer orderId, java.util.function.BiConsumer<LitemallOrderAggregate, String> enqueue) {
        try {
            LitemallOrderAggregate order = orderRepository
                    .findById(new org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId(orderId))
                    .orElse(null);
            if (order == null) {
                log.warn("Customer mail skipped: order {} not found after commit", orderId);
                return;
            }
            String email = buyerEmail(order);
            if (email == null) {
                return; // email-less buyer: silent skip, no row (documented semantics)
            }
            enqueue.accept(order, email);
        } catch (Throwable t) {
            // Catch-ALL: a mail bug must never kill the single worker thread.
            log.warn("Customer-mail enqueue crashed for order {}: {}", orderId, t.toString());
        }
    }

    private String buyerEmail(LitemallOrderAggregate order) {
        Integer userId = order.getUserId() == null ? null : order.getUserId().getId();
        if (userId == null) {
            return null;
        }
        LitemallUser user = userMapper.selectByPrimaryKey(userId);
        String email = user == null ? null : user.getEmail();
        return (email == null || email.isBlank()) ? null : email.trim();
    }

    // ------------------------------------------------------------------
    // Per-event template rendering
    // ------------------------------------------------------------------

    private void enqueuePaidMails(LitemallOrderAggregate order, String email) {
        insertRow(email, renderConfirmation(order));
        // Pickup orders get their redeem code at pay time (assigned inside the payment
        // transaction, so it is committed and readable here).
        if (order.isPickup()) {
            insertRow(email, MailTemplates.pickupCode(order.getOrderSn(),
                    pickupLocation(order), order.getVerifyCode()));
        }
    }

    /**
     * Rich confirmation (Wave 10): line items + amounts + delivery. The extra DB
     * read happens on the mail worker AFTER the payment committed; if it (or the
     * rendering) fails for any reason the customer still gets the minimal
     * orderSn+total mail — never nothing, never a throw.
     */
    private MailTemplates.RenderedMail renderConfirmation(LitemallOrderAggregate order) {
        try {
            List<LitemallOrderGoodsAggregate> orderGoods = orderGoodsRepository.findByOId(order.getOrderId());
            List<MailTemplates.OrderLine> lines = new ArrayList<>();
            for (LitemallOrderGoodsAggregate goods : orderGoods) {
                lines.add(new MailTemplates.OrderLine(goods.getGoodsName(),
                        specifications(goods.getSpecifications()),
                        goods.getNumber() == null ? 0 : goods.getNumber(),
                        money(goods.getPrice())));
            }
            return MailTemplates.orderConfirmation(new MailTemplates.OrderConfirmationDetails(
                    order.getOrderSn(),
                    payTime(order.getPayTime()),
                    lines,
                    money(order.getGoodsPrice()),
                    money(order.getFreightPrice()),
                    discount(order.getCouponPrice()),
                    moneyNonZero(order.getTaxPrice()),
                    money(order.getActualPrice()),
                    deliveryBlock(order)));
        } catch (Throwable t) {
            log.warn("Rich order-confirmation build failed for order {} — sending minimal body: {}",
                    order.getOrderId().getId(), t.toString());
            return MailTemplates.orderConfirmation(order.getOrderSn(), money(order.getActualPrice()));
        }
    }

    private String deliveryBlock(LitemallOrderAggregate order) {
        if (order.isPickup()) {
            return "Pickup at: " + pickupLocation(order)
                    + "\nYour pickup code arrives in a separate email.";
        }
        StringBuilder block = new StringBuilder();
        appendLine(block, "Consignee: ", order.getConsignee());
        appendLine(block, "Phone: ", order.getMobile());
        appendLine(block, "Address: ", order.getAddress());
        return block.toString();
    }

    private static void appendLine(StringBuilder block, String label, String value) {
        if (value != null && !value.isBlank()) {
            if (block.length() > 0) {
                block.append('\n');
            }
            block.append(label).append(value.trim());
        }
    }

    private static String specifications(String[] specifications) {
        if (specifications == null || specifications.length == 0) {
            return "";
        }
        return String.join(", ", specifications);
    }

    private static String payTime(LocalDateTime payTime) {
        return payTime == null ? "" : payTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    /** Coupon rendered as a negative amount; zero/absent = blank so the template omits the line. */
    private static String discount(LitemallMoney value) {
        String amount = moneyNonZero(value);
        return amount.isEmpty() ? "" : "-" + amount;
    }

    /** Like {@link #money} but a zero amount renders blank (the template omits blank lines). */
    private static String moneyNonZero(LitemallMoney value) {
        if (value == null || value.getAmount() == null || value.getAmount().signum() == 0) {
            return "";
        }
        return "$" + value.getAmount().toPlainString();
    }

    private void enqueueShippedMail(LitemallOrderAggregate order, String email) {
        insertRow(email, MailTemplates.shipped(order.getOrderSn(), order.getShipChannel(), order.getShipSn()));
    }

    private void enqueueRefundApprovedMail(LitemallOrderAggregate order, String email) {
        // approveAftersale stamps the (possibly partial) refunded amount on the order
        // row in the same transaction; fall back to the order total if it is missing.
        String amount = money(order.getRefundAmount());
        if (amount.isEmpty()) {
            amount = money(order.getActualPrice());
        }
        insertRow(email, MailTemplates.refundApproved(order.getOrderSn(), amount));
    }

    /** Pickup orders store {@code "PICKUP: <store name>"} in the address column. */
    private static String pickupLocation(LitemallOrderAggregate order) {
        String address = order.getAddress();
        if (address == null) {
            return "";
        }
        return address.startsWith("PICKUP: ") ? address.substring("PICKUP: ".length()) : address;
    }

    private static String money(LitemallMoney value) {
        return value == null || value.getAmount() == null ? "" : "$" + value.getAmount().toPlainString();
    }

    private void insertRow(String recipient, MailTemplates.RenderedMail mail) {
        LocalDateTime now = LocalDateTime.now();
        LitemallMailOutbox row = new LitemallMailOutbox();
        row.setRecipient(recipient);
        row.setSubject(mail.subject());
        row.setBody(mail.body());
        row.setTemplateKey(mail.templateKey());
        row.setStatus(LitemallMailOutbox.STATUS_PENDING);
        row.setAttempts(0);
        row.setSendAt(now);
        row.setAddTime(now);
        row.setUpdateTime(now);
        row.setDeleted(false);
        mailOutboxMapper.insert(row);
        log.debug("Enqueued {} mail (outbox row {}) to {}", mail.templateKey(), row.getId(), recipient);
    }
}
