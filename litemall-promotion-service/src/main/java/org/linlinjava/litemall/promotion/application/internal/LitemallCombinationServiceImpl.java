package org.linlinjava.litemall.promotion.application.internal;

import org.linlinjava.litemall.promotion.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.promotion.domain.events.combination.LitemallCombinationActivatedEvent;
import org.linlinjava.litemall.promotion.domain.events.combination.LitemallCombinationDefinedEvent;
import org.linlinjava.litemall.promotion.domain.events.combination.LitemallCombinationExpiredEvent;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCombinationAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallActivateCombinationCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallDefineCombinationCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallExpireCombinationCommand;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallCombinationRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCombinationStatus;
import org.linlinjava.litemall.promotion.domain.service.LitemallCombinationDomainService;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application service for the combination (group-buy) campaign-DEFINITION
 * vertical. Promotion owns the offer/rules only; participation/pink stays in
 * litemall-order (see the ADR). No pink/join logic lives here.
 */
@Service
@Transactional
public class LitemallCombinationServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(LitemallCombinationServiceImpl.class);

    private final LitemallCombinationRepository combinationRepository;
    private final LitemallCombinationDomainService combinationDomainService;
    private final LitemallDomainEventPublisher domainEventPublisher;

    public LitemallCombinationServiceImpl(LitemallCombinationRepository combinationRepository,
                                          LitemallCombinationDomainService combinationDomainService,
                                          LitemallDomainEventPublisher domainEventPublisher) {
        this.combinationRepository = combinationRepository;
        this.combinationDomainService = combinationDomainService;
        this.domainEventPublisher = domainEventPublisher;
    }

    /** Admin: define a campaign (created DRAFT). */
    public LitemallPromotionOperationResult defineCombination(LitemallDefineCombinationCommand command) {
        logger.info("Defining combination campaign: goodsId={}, title={}",
                command.getGoodsId(), command.getTitle());

        LitemallCombinationAggregate combination = LitemallCombinationAggregate.builder()
                .goodsId(command.getGoodsId())
                .title(command.getTitle())
                .picUrl(command.getPicUrl())
                .combinationPrice(command.getCombinationPrice() != null ? new LitemallMoney(command.getCombinationPrice()) : null)
                .originalPrice(command.getOriginalPrice() != null ? new LitemallMoney(command.getOriginalPrice()) : null)
                .requiredMembers(command.getRequiredMembers())
                .limitPerUser(command.getLimitPerUser())
                .startTime(command.getStartTime())
                .endTime(command.getEndTime())
                .status(LitemallCombinationStatus.DRAFT)
                .build();

        combinationDomainService.validateOffer(combination);
        combinationRepository.save(combination);

        domainEventPublisher.publish(new LitemallCombinationDefinedEvent(
                combination.getCombinationId(), combination.getGoodsId()));

        Map<String, Object> data = new HashMap<>();
        data.put("combinationId", combination.getCombinationId().getId());
        return LitemallPromotionOperationResult.combinationDefined(data);
    }

    /** Admin: make a DRAFT campaign customer-visible. */
    public LitemallPromotionOperationResult activateCombination(LitemallActivateCombinationCommand command) {
        Optional<LitemallCombinationAggregate> opt = combinationRepository.findById(command.getCombinationId());
        if (opt.isEmpty()) {
            return LitemallPromotionOperationResult.combinationStateChangeFailed("Combination campaign not found");
        }
        LitemallCombinationAggregate combination = opt.get();
        combination.activate();
        combinationRepository.save(combination);

        domainEventPublisher.publish(new LitemallCombinationActivatedEvent(combination.getCombinationId()));

        Map<String, Object> data = new HashMap<>();
        data.put("combinationId", combination.getCombinationId().getId());
        return LitemallPromotionOperationResult.combinationActivated(data);
    }

    /** Admin: expire/retire a campaign. */
    public LitemallPromotionOperationResult expireCombination(LitemallExpireCombinationCommand command) {
        Optional<LitemallCombinationAggregate> opt = combinationRepository.findById(command.getCombinationId());
        if (opt.isEmpty()) {
            return LitemallPromotionOperationResult.combinationStateChangeFailed("Combination campaign not found");
        }
        LitemallCombinationAggregate combination = opt.get();
        combination.expire();
        combinationRepository.save(combination);

        domainEventPublisher.publish(new LitemallCombinationExpiredEvent(combination.getCombinationId()));

        Map<String, Object> data = new HashMap<>();
        data.put("combinationId", combination.getCombinationId().getId());
        return LitemallPromotionOperationResult.combinationExpired(data);
    }

    // ----- read models -----

    @Transactional(readOnly = true)
    public List<LitemallCombinationAggregate> getActiveCombinations() {
        return combinationRepository.findActive();
    }

    @Transactional(readOnly = true)
    public Optional<LitemallCombinationAggregate> getCombination(LitemallCombinationId combinationId) {
        return combinationRepository.findById(combinationId);
    }

    @Transactional(readOnly = true)
    public List<LitemallCombinationAggregate> listCombinations() {
        return combinationRepository.findAll();
    }
}
