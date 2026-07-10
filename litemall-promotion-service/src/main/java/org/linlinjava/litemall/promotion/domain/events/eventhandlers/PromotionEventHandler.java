package org.linlinjava.litemall.promotion.domain.events.eventhandlers;

import org.linlinjava.litemall.promotion.domain.events.bargain.LitemallBargainHelpAppliedEvent;
import org.linlinjava.litemall.promotion.domain.events.bargain.LitemallBargainSessionCreatedEvent;
import org.linlinjava.litemall.promotion.domain.events.bargain.LitemallBargainSucceededEvent;
import org.linlinjava.litemall.promotion.domain.events.seckill.LitemallSeckillPurchasedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * In-process reactions to promotion domain events. Bound to
 * {@link TransactionPhase#AFTER_COMMIT} so side-effects only run once the
 * triggering write has durably committed ({@code fallbackExecution = true}
 * keeps them firing for events published outside a transaction). Cross-process
 * fan-out to Kafka is handled separately by the infrastructure StreamBridge so
 * this domain-layer handler stays free of messaging concerns.
 */
@Component
public class PromotionEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(PromotionEventHandler.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleSeckillPurchased(LitemallSeckillPurchasedEvent event) {
        logger.info("Seckill purchased: seckillId={}, userId={}, qty={}, price={}",
                event.getSeckillId().getId(),
                event.getUserId().getId(),
                event.getQuantity(),
                event.getPrice().getAmount());
        // TODO: Integrate with order service, send notifications, etc.
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleBargainSessionCreated(LitemallBargainSessionCreatedEvent event) {
        logger.info("Bargain session created: bargainId={}, userId={}, initialPrice={}",
                event.getBargainId().getId(),
                event.getUserId().getId(),
                event.getInitialPrice().getAmount());
        // TODO: Send share notifications, etc.
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleBargainHelpApplied(LitemallBargainHelpAppliedEvent event) {
        logger.info("Bargain help applied: bargainUserId={}, helperId={}, helpAmount={}, newPrice={}",
                event.getBargainUserId().getId(),
                event.getHelperId().getId(),
                event.getHelpAmount().getAmount(),
                event.getNewPrice().getAmount());
        // TODO: Notify session owner about help, etc.
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleBargainSucceeded(LitemallBargainSucceededEvent event) {
        logger.info("Bargain succeeded: bargainUserId={}, userId={}, bargainId={}, finalPrice={}",
                event.getBargainUserId().getId(),
                event.getUserId().getId(),
                event.getBargainId().getId(),
                event.getFinalPrice().getAmount());
        // TODO: Create order at final price, notify user, etc.
    }
}
