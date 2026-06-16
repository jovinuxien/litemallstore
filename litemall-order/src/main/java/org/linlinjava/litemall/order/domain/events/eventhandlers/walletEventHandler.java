package org.linlinjava.litemall.order.domain.events.eventhandlers;

import org.linlinjava.litemall.order.domain.events.wallet.LitemallWalletDebitedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * In-process consumer for wallet domain events. Runs AFTER_COMMIT so it never
 * observes a debit from a transaction that later rolled back (e.g. a payment
 * that failed after the wallet was debited). Mirrors {@code grouponEventHandler}.
 */
@Component
public class walletEventHandler {

    private static final Logger log = LoggerFactory.getLogger(walletEventHandler.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWalletDebited(LitemallWalletDebitedEvent event) {
        log.info("Wallet debited: walletId={} userId={} amount={} title={}",
                event.getWalletId(), event.getUserId(), event.getAmount(), event.getBillTitle());
    }
}
