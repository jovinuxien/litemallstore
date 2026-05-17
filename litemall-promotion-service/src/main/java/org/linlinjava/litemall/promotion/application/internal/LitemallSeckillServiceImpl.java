package org.linlinjava.litemall.promotion.application.internal;

import org.linlinjava.litemall.promotion.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.promotion.domain.events.seckill.LitemallSeckillPurchasedEvent;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallSeckillAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.LitemallJoinSeckillCommand;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallSeckillRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallSeckillId;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class LitemallSeckillServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(LitemallSeckillServiceImpl.class);

    private final LitemallSeckillRepository seckillRepository;
    private final LitemallDomainEventPublisher domainEventPublisher;

    @Autowired
    public LitemallSeckillServiceImpl(LitemallSeckillRepository seckillRepository,
                                       LitemallDomainEventPublisher domainEventPublisher) {
        this.seckillRepository = seckillRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * Join a seckill (flash sale).
     * 1. Load seckill aggregate
     * 2. Validate: active, stock available, user quota not exceeded
     * 3. Reserve stock (atomic update)
     * 4. Publish LitemallSeckillPurchasedEvent
     * 5. Return result with price info for order creation
     */
    public LitemallPromotionOperationResult joinSeckill(LitemallJoinSeckillCommand command) {
        logger.info("Processing seckill join: seckillId={}, userId={}, qty={}",
                command.getSeckillId().getId(), command.getUserId().getId(), command.getQuantity());

        // Step 1: Load seckill aggregate
        Optional<LitemallSeckillAggregate> seckillOpt = seckillRepository.findById(command.getSeckillId());
        if (seckillOpt.isEmpty()) {
            return LitemallPromotionOperationResult.seckillJoinFailed("Seckill not found");
        }

        LitemallSeckillAggregate seckill = seckillOpt.get();

        // Step 2: Validate active status
        if (!seckill.isActive()) {
            return LitemallPromotionOperationResult.invalidState(
                    LitemallPromotionOperationResult.OperationType.JOIN_SECKILL,
                    "Seckill is not active");
        }

        // Step 2: Validate stock availability
        if (!seckill.isAvailable(command.getQuantity())) {
            return LitemallPromotionOperationResult.seckillJoinFailed("Insufficient stock");
        }

        // Step 2: Validate user quota (simplified - in real impl, query user purchase history)
        // canUserPurchase check: assuming 0 already bought for now
        if (!seckill.canUserPurchase(0)) {
            return LitemallPromotionOperationResult.seckillJoinFailed("User quota exceeded");
        }

        // Step 3: Reserve stock (atomic update)
        int updated = seckillRepository.updateStock(command.getSeckillId(), -command.getQuantity());
        if (updated <= 0) {
            return LitemallPromotionOperationResult.seckillJoinFailed("Stock reservation failed - concurrent update detected");
        }

        // Step 4: Publish domain event
        LitemallSeckillPurchasedEvent event = new LitemallSeckillPurchasedEvent(
                command.getSeckillId(),
                command.getUserId(),
                command.getQuantity(),
                seckill.getPrice()
        );
        domainEventPublisher.publish(event);

        // Step 5: Return result with price info
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("seckillId", command.getSeckillId().getId());
        resultData.put("userId", command.getUserId().getId());
        resultData.put("quantity", command.getQuantity());
        resultData.put("price", seckill.getPrice().getAmount());
        resultData.put("totalPrice", seckill.getPrice().getAmount().multiply(
                java.math.BigDecimal.valueOf(command.getQuantity())));
        resultData.put("goodsId", seckill.getGoodsId());
        resultData.put("goodsName", seckill.getGoodsName());

        return LitemallPromotionOperationResult.seckillJoinSuccess(resultData);
    }

    @Transactional(readOnly = true)
    public List<LitemallSeckillAggregate> getActiveSeckills() {
        return seckillRepository.findAllActive();
    }

    @Transactional(readOnly = true)
    public Optional<LitemallSeckillAggregate> getSeckill(LitemallSeckillId seckillId) {
        return seckillRepository.findById(seckillId);
    }
}
