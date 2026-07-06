package org.linlinjava.litemall.order.domain.model.commands.payment;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Data;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.payment.PaymentMethod;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.payment.LitemallPaymentInfo;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

@Data
public class LitemallOrderPaymentCommand {


    private final LitemallOrderId orderId;
    private final LitemallUserId userId;

    /**
     * Full Stripe-coupled payment info. Optional: the WALLET path debits the
     * order's actual price server-side (no Stripe object needed) and the
     * client-confirmed CARD path identifies the charge by {@link #paymentReference}
     * instead, so this is {@code null} for both of those flows.
     */
    private final LitemallPaymentInfo paymentInfo;

    /**
     * Selected payment method for this command. Carries the domain
     * {@link PaymentMethod} (distinct from the Stripe type inside
     * {@link LitemallPaymentInfo}); the orchestrator branches on this — e.g.
     * {@code WALLET} routes to the wallet-debit path.
     */
    private final PaymentMethod paymentMethod;

    /**
     * Client-confirmed Stripe PaymentIntent id for the CARD path. Per the
     * accepted boundary (client-confirmed Stripe — see
     * {@code docs/handoff-gateway-api-order-payment.md}) the SPA confirms the
     * PaymentIntent and {@code /actions/pay} records the result by this id; the
     * order service does not charge Stripe server-side. {@code null} for WALLET.
     */
    private final String paymentReference;

    public LitemallOrderPaymentCommand(LitemallOrderId orderId, LitemallUserId userId, LitemallPaymentInfo info) {
        this(orderId, userId, info, null, null);
    }

    public LitemallOrderPaymentCommand(LitemallOrderId orderId, LitemallUserId userId, LitemallPaymentInfo info,
                                       PaymentMethod paymentMethod) {
        this(orderId, userId, info, paymentMethod, null);
    }

    /**
     * Wallet / client-confirmed-card constructor: no Stripe-coupled
     * {@link LitemallPaymentInfo} required. WALLET passes a {@code null}
     * reference; CARD passes the client-confirmed PaymentIntent id.
     */
    public LitemallOrderPaymentCommand(LitemallOrderId orderId, LitemallUserId userId,
                                       PaymentMethod paymentMethod, String paymentReference) {
        this(orderId, userId, null, paymentMethod, paymentReference);
    }

    public LitemallOrderPaymentCommand(LitemallOrderId orderId, LitemallUserId userId, LitemallPaymentInfo info,
                                       PaymentMethod paymentMethod, String paymentReference) {

        if (orderId == null || orderId.getId() <= 0) {
            throw new IllegalArgumentException("Order ID must be a positive integer.");
        }

        if (userId == null || userId.getId() <= 0) {
            throw new IllegalArgumentException("User ID must be a positive integer.");
        }

        this.userId = userId;
        this.orderId = orderId;
        this.paymentInfo = info;
        this.paymentMethod = paymentMethod;
        this.paymentReference = paymentReference;
    }

}
