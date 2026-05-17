package org.linlinjava.litemall.loyalty.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.loyalty.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.loyalty.domain.events.loyalty.LitemallUserLevelUpEvent;
import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallSystemLevelAggregate;
import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallUserLevelAggregate;
import org.linlinjava.litemall.loyalty.domain.model.commands.LitemallEarnExperienceCommand;
import org.linlinjava.litemall.loyalty.domain.model.repositories.LitemallExperienceRepository;
import org.linlinjava.litemall.loyalty.domain.model.repositories.LitemallUserLevelRepository;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallExperienceId;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserLevelRecordId;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.enums.LitemallLevelStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Random;

@Slf4j
@Service
@Transactional
public class LitemallLevelServiceLayer {

    private final LitemallExperienceRepository experienceRepository;
    private final LitemallUserLevelRepository userLevelRepository;
    private final LitemallDomainEventPublisher domainEventPublisher;

    public LitemallLevelServiceLayer(LitemallExperienceRepository experienceRepository,
                                     LitemallUserLevelRepository userLevelRepository,
                                     LitemallDomainEventPublisher domainEventPublisher) {
        this.experienceRepository = experienceRepository;
        this.userLevelRepository = userLevelRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * Earn experience points for a user.
     */
    public int earnExperience(LitemallEarnExperienceCommand command) {
        LitemallUserId userId = new LitemallUserId(command.getUserId());
        int current = getBalanceSafe(userId);
        int newBalance = current + command.getExperience();

        LitemallExperienceId recordId = new LitemallExperienceId(generateRecordId());
        experienceRepository.addRecord(
                recordId, userId, command.getExperience(), newBalance,
                command.getTitle(), command.getLinkId(), command.getLinkType());
        experienceRepository.updateUserExperience(userId, newBalance);

        log.info("Experience earned: userId={}, experience={}, newBalance={}",
                command.getUserId(), command.getExperience(), newBalance);
        return newBalance;
    }

    /**
     * Check if the user qualifies for a level-up and upgrade if so.
     */
    public Optional<LitemallUserLevelAggregate> checkAndUpgrade(LitemallUserId userId) {
        int currentXp = getBalanceSafe(userId);
        Optional<LitemallSystemLevelAggregate> nextLevel = userLevelRepository.findNextLevelByExperience(currentXp);

        if (!nextLevel.isPresent()) {
            return Optional.empty();
        }

        LitemallSystemLevelAggregate levelDef = nextLevel.get();
        Optional<LitemallUserLevelAggregate> currentRecord = userLevelRepository.findCurrentByUserId(userId);

        Byte previousGrade = currentRecord.map(LitemallUserLevelAggregate::getGrade).orElse((byte) 0);
        Byte newGrade = levelDef.getLevel();

        if (previousGrade >= newGrade) {
            // Already at this level or higher
            return Optional.empty();
        }

        // Create or update the level record
        LitemallUserLevelAggregate levelRecord = currentRecord.orElseGet(LitemallUserLevelAggregate::new);
        if (levelRecord.getLevelRecordId() == null) {
            levelRecord.setLevelRecordId(new LitemallUserLevelRecordId(generateRecordId()));
        }
        levelRecord.setUserId(userId);
        levelRecord.setLevelId(levelDef.getSystemLevelId().getId());
        levelRecord.setGrade(newGrade);
        levelRecord.setExperience(currentXp);
        levelRecord.setStatus(LitemallLevelStatus.ACTIVE.getCode());
        levelRecord.setLevelDefinition(levelDef);

        userLevelRepository.save(levelRecord);

        domainEventPublisher.publish(new LitemallUserLevelUpEvent(
                userId.getId(), previousGrade, newGrade, levelDef.getSystemLevelId().getId()));

        log.info("Level up: userId={}, previousGrade={}, newGrade={}", userId.getId(), previousGrade, newGrade);
        return Optional.of(levelRecord);
    }

    public int getBalanceSafe(LitemallUserId userId) {
        Integer balance = experienceRepository.getBalance(userId);
        return balance == null ? 0 : balance;
    }

    private int generateRecordId() {
        return Math.abs(new Random().nextInt(Integer.MAX_VALUE - 1)) + 1;
    }
}
