package org.linlinjava.litemall.promotion.application.internal;

import org.linlinjava.litemall.promotion.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.promotion.domain.events.bargain.LitemallBargainHelpAppliedEvent;
import org.linlinjava.litemall.promotion.domain.events.bargain.LitemallBargainSessionCreatedEvent;
import org.linlinjava.litemall.promotion.domain.events.bargain.LitemallBargainSucceededEvent;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallBargainAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallBargainHelpAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallBargainUserAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.LitemallCreateBargainSessionCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.LitemallHelpBargainCommand;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallBargainHelpRepository;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallBargainRepository;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallBargainUserRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallBargainUserStatus;
import org.linlinjava.litemall.promotion.domain.service.LitemallBargainDomainService;
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
public class LitemallBargainServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(LitemallBargainServiceImpl.class);

    private final LitemallBargainRepository bargainRepository;
    private final LitemallBargainUserRepository bargainUserRepository;
    private final LitemallBargainHelpRepository bargainHelpRepository;
    private final LitemallBargainDomainService bargainDomainService;
    private final LitemallDomainEventPublisher domainEventPublisher;

    @Autowired
    public LitemallBargainServiceImpl(LitemallBargainRepository bargainRepository,
                                       LitemallBargainUserRepository bargainUserRepository,
                                       LitemallBargainHelpRepository bargainHelpRepository,
                                       LitemallBargainDomainService bargainDomainService,
                                       LitemallDomainEventPublisher domainEventPublisher) {
        this.bargainRepository = bargainRepository;
        this.bargainUserRepository = bargainUserRepository;
        this.bargainHelpRepository = bargainHelpRepository;
        this.bargainDomainService = bargainDomainService;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * Create a bargain session for a user.
     * 1. Load bargain aggregate, validate active
     * 2. Check user hasn't already started a session for this bargain
     * 3. Create LitemallBargainUserAggregate with initial price = bargain.price
     * 4. Publish LitemallBargainSessionCreatedEvent
     */
    public LitemallPromotionOperationResult createBargainSession(LitemallCreateBargainSessionCommand command) {
        logger.info("Creating bargain session: bargainId={}, userId={}",
                command.getBargainId().getId(), command.getUserId().getId());

        // Step 1: Load bargain aggregate and validate
        Optional<LitemallBargainAggregate> bargainOpt = bargainRepository.findById(command.getBargainId());
        if (bargainOpt.isEmpty()) {
            return LitemallPromotionOperationResult.bargainSessionFailed("Bargain not found");
        }

        LitemallBargainAggregate bargain = bargainOpt.get();
        if (!bargain.isActive()) {
            return LitemallPromotionOperationResult.invalidState(
                    LitemallPromotionOperationResult.OperationType.CREATE_BARGAIN,
                    "Bargain is not active");
        }

        if (!bargain.isAvailable()) {
            return LitemallPromotionOperationResult.bargainSessionFailed("Bargain is out of stock");
        }

        // Step 2: Check user hasn't already started a session
        Optional<LitemallBargainUserAggregate> existingSession =
                bargainUserRepository.findByUserAndBargain(command.getUserId(), command.getBargainId());
        if (existingSession.isPresent()) {
            return LitemallPromotionOperationResult.bargainSessionFailed(
                    "User already has an active bargain session for this product");
        }

        // Step 3: Create bargain user aggregate with initial price = bargain.price
        LitemallBargainUserAggregate bargainUser = LitemallBargainUserAggregate.builder()
                .userId(command.getUserId())
                .bargainId(command.getBargainId())
                .bargainPriceMin(bargain.getMinPrice())
                .bargainPrice(bargain.getPrice())
                .status(LitemallBargainUserStatus.ONGOING)
                .build();

        bargainUserRepository.add(bargainUser);

        // Step 4: Publish event
        LitemallBargainSessionCreatedEvent event = new LitemallBargainSessionCreatedEvent(
                bargainUser.getBargainUserId(),
                command.getUserId(),
                command.getBargainId(),
                bargain.getPrice()
        );
        domainEventPublisher.publish(event);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("userId", command.getUserId().getId());
        resultData.put("bargainId", command.getBargainId().getId());
        resultData.put("initialPrice", bargain.getPrice().getAmount());
        resultData.put("targetPrice", bargain.getMinPrice().getAmount());

        return LitemallPromotionOperationResult.bargainSessionCreated(resultData);
    }

    /**
     * Help a friend bargain.
     * 1. Load bargainUser aggregate
     * 2. Validate: still ongoing, helper != session owner
     * 3. Calculate random help amount between min/max
     * 4. Apply help (reduce bargainPrice)
     * 5. Check if target reached → mark as success
     * 6. Publish LitemallBargainHelpAppliedEvent (+ LitemallBargainSucceededEvent if done)
     */
    public LitemallPromotionOperationResult helpBargain(LitemallHelpBargainCommand command) {
        logger.info("Applying bargain help: bargainUserId={}, helperId={}",
                command.getBargainUserId().getId(), command.getHelperId().getId());

        // Step 1: Load bargainUser aggregate
        Optional<LitemallBargainUserAggregate> bargainUserOpt =
                bargainUserRepository.findById(command.getBargainUserId());
        if (bargainUserOpt.isEmpty()) {
            return LitemallPromotionOperationResult.helpBargainFailed("Bargain session not found");
        }

        LitemallBargainUserAggregate bargainUser = bargainUserOpt.get();

        // Step 2: Validate session is ongoing
        if (!bargainUser.isOngoing()) {
            return LitemallPromotionOperationResult.invalidState(
                    LitemallPromotionOperationResult.OperationType.HELP_BARGAIN,
                    "Bargain session is no longer active");
        }

        // Step 2: Validate helper is not the session owner
        if (bargainUser.getUserId().getId().equals(command.getHelperId().getId())) {
            return LitemallPromotionOperationResult.helpBargainFailed(
                    "Cannot help your own bargain session");
        }

        // Step 3: Load bargain to get min/max help amounts
        Optional<LitemallBargainAggregate> bargainOpt = bargainRepository.findById(bargainUser.getBargainId());
        if (bargainOpt.isEmpty()) {
            return LitemallPromotionOperationResult.helpBargainFailed("Bargain configuration not found");
        }

        LitemallBargainAggregate bargain = bargainOpt.get();

        // Step 3: Calculate random help amount
        LitemallMoney helpAmount = bargainDomainService.calculateHelpAmount(bargain);

        // Step 4: Apply help (reduce bargainPrice via domain model)
        LitemallMoney priceBeforeHelp = bargainUser.getBargainPrice();
        bargainUser.applyHelp(helpAmount);
        LitemallMoney newPrice = bargainUser.getBargainPrice();

        // Save the help record
        LitemallBargainHelpAggregate helpRecord = LitemallBargainHelpAggregate.builder()
                .helperId(command.getHelperId())
                .bargainId(bargainUser.getBargainId())
                .bargainUserId(command.getBargainUserId())
                .helpAmount(helpAmount)
                .build();
        bargainHelpRepository.add(helpRecord);

        // Update bargain price in repository
        bargainUserRepository.updateBargainPrice(command.getBargainUserId(), newPrice);

        // Step 5: Check if target reached
        boolean succeeded = bargainDomainService.isTargetReached(bargainUser);
        if (succeeded) {
            bargainUserRepository.updateStatus(command.getBargainUserId(), LitemallBargainUserStatus.SUCCESS);

            // Publish success event
            domainEventPublisher.publish(new LitemallBargainSucceededEvent(
                    command.getBargainUserId(),
                    bargainUser.getUserId(),
                    bargainUser.getBargainId(),
                    newPrice
            ));
        }

        // Step 6: Publish help applied event
        domainEventPublisher.publish(new LitemallBargainHelpAppliedEvent(
                command.getBargainUserId(),
                command.getHelperId(),
                helpAmount,
                newPrice
        ));

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("bargainUserId", command.getBargainUserId().getId());
        resultData.put("helperId", command.getHelperId().getId());
        resultData.put("helpAmount", helpAmount.getAmount());
        resultData.put("newPrice", newPrice.getAmount());
        resultData.put("targetReached", succeeded);

        return LitemallPromotionOperationResult.helpBargainSuccess(resultData);
    }

    @Transactional(readOnly = true)
    public List<LitemallBargainAggregate> getActiveBargains() {
        return bargainRepository.findAllActive();
    }

    @Transactional(readOnly = true)
    public Optional<LitemallBargainAggregate> getBargain(LitemallBargainId bargainId) {
        return bargainRepository.findById(bargainId);
    }

    @Transactional(readOnly = true)
    public Optional<LitemallBargainUserAggregate> getBargainSession(
            org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainUserId bargainUserId) {
        return bargainUserRepository.findById(bargainUserId);
    }

    @Transactional(readOnly = true)
    public int getHelpCount(
            org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainUserId bargainUserId) {
        return bargainHelpRepository.countByBargainUserId(bargainUserId);
    }
}
