package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallBillAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.wallet.LitemallBillId;

import java.util.List;
import java.util.Optional;

public interface LitemallBillRepository {

    /**
     * Business keys stamped on order-related wallet bills by the payment/refund
     * commands. The ledger lookups below filter by them, so the writers (the
     * orchestrator's debit/credit commands) must use these same constants.
     */
    String CATEGORY_ORDER = "ORDER";
    String TYPE_PAYMENT = "PAYMENT";
    String TYPE_REFUND = "REFUND";

    void add(LitemallBillAggregate bill);

    List<LitemallBillAggregate> findByUserId(LitemallUserId userId);

    LitemallBillAggregate findById(LitemallBillId billId);

    /**
     * The wallet DEBIT recorded when this order was paid ({@link #CATEGORY_ORDER}/
     * {@link #TYPE_PAYMENT}/{@code linkId=orderRef}) — the authoritative captured
     * amount for refund settlement. Empty when the order was not wallet-paid (or
     * had a non-positive payable, which skips the debit).
     */
    Optional<LitemallBillAggregate> findOrderPaymentDebit(LitemallUserId userId, String orderRef);

    /**
     * Whether a refund CREDIT for this order is already on the ledger
     * ({@link #CATEGORY_ORDER}/{@link #TYPE_REFUND}/{@code linkId=orderRef}).
     * Idempotency guard: a retried/replayed refund approval must not credit twice.
     */
    boolean orderRefundCreditExists(LitemallUserId userId, String orderRef);
}
