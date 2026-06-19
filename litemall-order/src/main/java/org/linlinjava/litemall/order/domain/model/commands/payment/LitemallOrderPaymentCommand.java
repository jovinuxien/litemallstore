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
    private final LitemallPaymentInfo paymentInfo;

    /**
     * Selected payment method for this command. Carries the domain
     * {@link PaymentMethod} (distinct from the Stripe type inside
     * {@link LitemallPaymentInfo}); the orchestrator branches on this — e.g.
     * {@code WALLET} routes to the wallet-debit path.
     */
    private final PaymentMethod paymentMethod;

    public LitemallOrderPaymentCommand(LitemallOrderId orderId, LitemallUserId userId, LitemallPaymentInfo info) {
        this(orderId, userId, info, null);
    }

    public LitemallOrderPaymentCommand(LitemallOrderId orderId, LitemallUserId userId, LitemallPaymentInfo info,
                                       PaymentMethod paymentMethod) {

        if (orderId == null || orderId.getId() <= 0) {
            throw new IllegalArgumentException("Order ID must be a positive integer.");
        }

        if (userId == null || userId.getId() <= 0) {
            throw new IllegalArgumentException("User ID must be a positive integer.");
        }

        if(info == null) {
            throw new IllegalArgumentException("Payment information must not be null.");
        }
        this.userId = userId;
        this.orderId = orderId;
        this.paymentInfo = info;
        this.paymentMethod = paymentMethod;
    }

}
