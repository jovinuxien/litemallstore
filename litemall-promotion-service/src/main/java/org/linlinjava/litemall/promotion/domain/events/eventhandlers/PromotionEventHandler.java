package org.linlinjava.litemall.promotion.domain.events.eventhandlers;

import org.linlinjava.litemall.promotion.domain.events.bargain.LitemallBargainHelpAppliedEvent;
import org.linlinjava.litemall.promotion.domain.events.bargain.LitemallBargainSessionCreatedEvent;
import org.linlinjava.litemall.promotion.domain.events.bargain.LitemallBargainSucceededEvent;
import org.linlinjava.litemall.promotion.domain.events.seckill.LitemallSeckillPurchasedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PromotionEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(PromotionEventHandler.class);

    @EventListener
    public void handleSeckillPurchased(LitemallSeckillPurchasedEvent event) {
        logger.info("Seckill purchased: seckillId={}, userId={}, qty={}, price={}",
                event.getSeckillId().getId(),
                event.getUserId().getId(),
                event.getQuantity(),
                event.getPrice().getAmount());
        // TODO: Integrate with order service, send notifications, etc.
    }

    @EventListener
    public void handleBargainSessionCreated(LitemallBargainSessionCreatedEvent event) {
        logger.info("Bargain session created: bargainId={}, userId={}, initialPrice={}",
                event.getBargainId().getId(),
                event.getUserId().getId(),
                event.getInitialPrice().getAmount());
        // TODO: Send share notifications, etc.
    }

    @EventListener
    public void handleBargainHelpApplied(LitemallBargainHelpAppliedEvent event) {
        logger.info("Bargain help applied: bargainUserId={}, helperId={}, helpAmount={}, newPrice={}",
                event.getBargainUserId().getId(),
                event.getHelperId().getId(),
                event.getHelpAmount().getAmount(),
                event.getNewPrice().getAmount());
        // TODO: Notify session owner about help, etc.
    }

    @EventListener
    public void handleBargainSucceeded(LitemallBargainSucceededEvent event) {
        logger.info("Bargain succeeded: bargainUserId={}, userId={}, bargainId={}, finalPrice={}",
                event.getBargainUserId().getId(),
                event.getUserId().getId(),
                event.getBargainId().getId(),
                event.getFinalPrice().getAmount());
        // TODO: Create order at final price, notify user, etc.
    }
}
