package org.linlinjava.litemall.promotion.application.internal;

import org.linlinjava.litemall.promotion.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.promotion.domain.events.combination.LitemallCombinationActivatedEvent;
import org.linlinjava.litemall.promotion.domain.events.combination.LitemallCombinationDefinedEvent;
import org.linlinjava.litemall.promotion.domain.events.combination.LitemallCombinationExpiredEvent;
import org.linlinjava.litemall.promotion.domain.events.combination.LitemallGroupCompletedEvent;
import org.linlinjava.litemall.promotion.domain.events.combination.LitemallGroupExpiredEvent;
import org.linlinjava.litemall.promotion.domain.events.combination.LitemallGroupMemberJoinedEvent;
import org.linlinjava.litemall.promotion.domain.events.combination.LitemallGroupStartedEvent;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCombinationAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCombinationPinkAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallActivateCombinationCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallDefineCombinationCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallExpireCombinationCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallJoinGroupCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallStartGroupCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallUpdateCombinationCommand;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallCombinationPinkRepository;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallCombinationRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationPinkId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCombinationPinkStatus;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCombinationStatus;
import org.linlinjava.litemall.promotion.domain.service.LitemallCombinationDomainService;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.linlinjava.litemall.promotion.infrastructure.configuration.PromotionGrouponProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application service for the combination (group-buy) vertical: the campaign
 * DEFINITION (offer/rules) plus group PARTICIPATION ("pink", V30). Promotion
 * owns both since Wave 2 — see the updated ADR; litemall-order's legacy
 * groupon tables are untouched and order consumes the priced-submit spec.
 */
@Service
@Transactional
public class LitemallCombinationServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(LitemallCombinationServiceImpl.class);

    private final LitemallCombinationRepository combinationRepository;
    private final LitemallCombinationPinkRepository pinkRepository;
    private final LitemallCombinationDomainService combinationDomainService;
    private final LitemallDomainEventPublisher domainEventPublisher;
    private final PromotionGrouponProperties grouponProperties;

    public LitemallCombinationServiceImpl(LitemallCombinationRepository combinationRepository,
                                          LitemallCombinationPinkRepository pinkRepository,
                                          LitemallCombinationDomainService combinationDomainService,
                                          LitemallDomainEventPublisher domainEventPublisher,
                                          PromotionGrouponProperties grouponProperties) {
        this.combinationRepository = combinationRepository;
        this.pinkRepository = pinkRepository;
        this.combinationDomainService = combinationDomainService;
        this.domainEventPublisher = domainEventPublisher;
        this.grouponProperties = grouponProperties;
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
                .limitPerUser(command.getLimitPerUser() != null ? command.getLimitPerUser() : 0)
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

    /** Admin: update a campaign definition; null command fields stay unchanged. */
    public LitemallPromotionOperationResult updateCombination(LitemallCombinationId combinationId,
                                                              LitemallUpdateCombinationCommand command) {
        Optional<LitemallCombinationAggregate> opt = combinationRepository.findById(combinationId);
        if (opt.isEmpty()) {
            return LitemallPromotionOperationResult.combinationStateChangeFailed("Combination campaign not found");
        }
        LitemallCombinationAggregate combination = opt.get();

        if (command.getTitle() != null) combination.setTitle(command.getTitle());
        if (command.getPicUrl() != null) combination.setPicUrl(command.getPicUrl());
        if (command.getCombinationPrice() != null) combination.setCombinationPrice(new LitemallMoney(command.getCombinationPrice()));
        if (command.getOriginalPrice() != null) combination.setOriginalPrice(new LitemallMoney(command.getOriginalPrice()));
        if (command.getRequiredMembers() != null) combination.setRequiredMembers(command.getRequiredMembers());
        if (command.getLimitPerUser() != null) combination.setLimitPerUser(command.getLimitPerUser());
        if (command.getStartTime() != null) combination.setStartTime(command.getStartTime());
        if (command.getEndTime() != null) combination.setEndTime(command.getEndTime());

        combinationDomainService.validateOffer(combination);
        combinationRepository.save(combination);

        Map<String, Object> data = new HashMap<>();
        data.put("combinationId", combination.getCombinationId().getId());
        return LitemallPromotionOperationResult.combinationUpdated(data);
    }

    /** Admin: logical delete; running groups keep their snapshots. */
    public LitemallPromotionOperationResult deleteCombination(LitemallCombinationId combinationId) {
        Optional<LitemallCombinationAggregate> opt = combinationRepository.findById(combinationId);
        if (opt.isEmpty()) {
            return LitemallPromotionOperationResult.combinationStateChangeFailed("Combination campaign not found");
        }
        combinationRepository.delete(combinationId);

        Map<String, Object> data = new HashMap<>();
        data.put("combinationId", combinationId.getId());
        return LitemallPromotionOperationResult.combinationDeleted(data);
    }

    // =========================================================================
    // GROUP PARTICIPATION ("pink")
    // =========================================================================

    /**
     * Customer: start a new group as its leader. Snapshots the campaign's
     * headcount and computes the fill deadline (configured TTL, capped at the
     * campaign end).
     */
    public LitemallPromotionOperationResult startGroup(LitemallStartGroupCommand command) {
        logger.info("Starting group: combinationId={}, userId={}",
                command.getCombinationId().getId(), command.getUserId().getId());

        Optional<LitemallCombinationAggregate> opt =
                combinationRepository.findById(command.getCombinationId());
        if (opt.isEmpty()) {
            return LitemallPromotionOperationResult.groupStartFailed("Combination campaign not found");
        }
        LitemallCombinationAggregate combination = opt.get();

        LocalDateTime now = LocalDateTime.now();
        if (!combination.canStartGroupon(now)) {
            return LitemallPromotionOperationResult.groupStartFailed(
                    "Campaign is not active or outside its window");
        }

        LocalDateTime expireTime = now.plusHours(grouponProperties.getGroupTtlHours());
        if (combination.getEndTime() != null && expireTime.isAfter(combination.getEndTime())) {
            expireTime = combination.getEndTime();
        }

        LitemallCombinationPinkAggregate leader = LitemallCombinationPinkAggregate.builder()
                .combinationId(command.getCombinationId())
                .userId(command.getUserId())
                .requiredMembers(combination.getRequiredMembers())
                .expireTime(expireTime)
                .status(LitemallCombinationPinkStatus.PENDING)
                .build();
        pinkRepository.add(leader);

        domainEventPublisher.publish(new LitemallGroupStartedEvent(
                leader.getPinkId(), command.getCombinationId(), command.getUserId()));

        Map<String, Object> data = new HashMap<>();
        data.put("pinkId", leader.getPinkId().getId());
        data.put("combinationId", command.getCombinationId().getId());
        data.put("requiredMembers", combination.getRequiredMembers());
        data.put("expireTime", expireTime);
        return LitemallPromotionOperationResult.groupStarted(data);
    }

    /**
     * Customer: join an open group by its leader slot id. Rejects a full,
     * expired, non-pending or double-joined group; completes the group when the
     * required headcount is reached.
     */
    public LitemallPromotionOperationResult joinGroup(LitemallJoinGroupCommand command) {
        logger.info("Joining group: pinkId={}, userId={}",
                command.getPinkId().getId(), command.getUserId().getId());

        Optional<LitemallCombinationPinkAggregate> leaderOpt =
                pinkRepository.findById(command.getPinkId());
        if (leaderOpt.isEmpty()) {
            return LitemallPromotionOperationResult.groupJoinFailed("Group not found");
        }
        LitemallCombinationPinkAggregate leader = leaderOpt.get();
        if (!leader.isLeader()) {
            return LitemallPromotionOperationResult.groupJoinFailed(
                    "Not a group leader slot — join via the leader's pink id");
        }
        if (!leader.isPending()) {
            return LitemallPromotionOperationResult.groupJoinFailed("Group is no longer open");
        }
        LocalDateTime now = LocalDateTime.now();
        if (leader.isExpired(now)) {
            return LitemallPromotionOperationResult.groupJoinFailed("Group has expired");
        }

        // Wave 21: released slots (status FAILED while the group is pending) no
        // longer occupy a seat — count and match against ACTIVE slots only, so
        // a freed seat is joinable again (including by the user who released).
        List<LitemallCombinationPinkAggregate> activeSlots =
                pinkRepository.findGroup(leader.getPinkId()).stream()
                        .filter(s -> !s.isReleasedOrFailed())
                        .collect(java.util.stream.Collectors.toList());
        boolean alreadyIn = activeSlots.stream().anyMatch(s -> s.isOwnedBy(command.getUserId()));
        if (alreadyIn) {
            return LitemallPromotionOperationResult.groupJoinFailed("Already a member of this group");
        }
        int required = leader.getRequiredMembers() != null ? leader.getRequiredMembers() : 2;
        if (activeSlots.size() >= required) {
            return LitemallPromotionOperationResult.groupJoinFailed("Group is already full");
        }

        LitemallCombinationPinkAggregate member = LitemallCombinationPinkAggregate.builder()
                .combinationId(leader.getCombinationId())
                .headId(leader.getPinkId())
                .userId(command.getUserId())
                .requiredMembers(leader.getRequiredMembers())
                .expireTime(leader.getExpireTime())
                .status(LitemallCombinationPinkStatus.PENDING)
                .build();
        pinkRepository.add(member);

        int memberCount = activeSlots.size() + 1;
        domainEventPublisher.publish(new LitemallGroupMemberJoinedEvent(
                leader.getPinkId(), leader.getCombinationId(), command.getUserId(), memberCount));

        boolean completed = memberCount >= required;
        if (completed) {
            completeGroup(leader, memberCount);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("pinkId", member.getPinkId().getId());
        data.put("groupPinkId", leader.getPinkId().getId());
        data.put("memberCount", memberCount);
        data.put("requiredMembers", required);
        data.put("completed", completed);
        return LitemallPromotionOperationResult.groupJoined(data);
    }

    private void completeGroup(LitemallCombinationPinkAggregate leader, int memberCount) {
        List<Integer> memberPinkIds = new ArrayList<>();
        for (LitemallCombinationPinkAggregate slot : pinkRepository.findGroup(leader.getPinkId())) {
            if (slot.isPending()) {
                slot.complete();
                pinkRepository.update(slot);
                memberPinkIds.add(slot.getPinkId().getId());
            }
        }
        domainEventPublisher.publish(new LitemallGroupCompletedEvent(
                leader.getPinkId(), leader.getCombinationId(), memberCount, memberPinkIds));
        logger.info("Group {} completed with {} members", leader.getPinkId().getId(), memberCount);
    }

    /**
     * Sweep: fail every pending group whose fill deadline passed, and expire
     * ACTIVE campaigns past their end time. Invoked by the scheduled sweeper.
     * Returns the number of groups failed.
     */
    public int expireOverdue() {
        LocalDateTime now = LocalDateTime.now();
        int failedGroups = 0;
        for (LitemallCombinationPinkAggregate leader : pinkRepository.findExpiredPendingLeaders(now)) {
            int memberCount = 0;
            List<Integer> memberPinkIds = new ArrayList<>();
            for (LitemallCombinationPinkAggregate slot : pinkRepository.findGroup(leader.getPinkId())) {
                if (slot.isPending()) {
                    slot.fail();
                    pinkRepository.update(slot);
                    memberPinkIds.add(slot.getPinkId().getId());
                }
                memberCount++;
            }
            domainEventPublisher.publish(new LitemallGroupExpiredEvent(
                    leader.getPinkId(), leader.getCombinationId(), memberCount, memberPinkIds));
            failedGroups++;
        }
        for (LitemallCombinationAggregate combination : combinationRepository.findActive()) {
            if (combination.isExpired(now)) {
                combination.expire();
                combinationRepository.save(combination);
                domainEventPublisher.publish(
                        new LitemallCombinationExpiredEvent(combination.getCombinationId()));
            }
        }
        if (failedGroups > 0) {
            logger.info("Expiry sweep failed {} overdue group(s)", failedGroups);
        }
        return failedGroups;
    }

    // =========================================================================
    // WAVE 21 — ORDER LINKAGE (priced submit follow-ups)
    // =========================================================================

    /**
     * Order backfills the buyer's slot with the placed order id (machine token
     * + forwarded {@code X-User-Id}). CAS semantics: the slot's {@code orderId}
     * is set only when currently null; re-attaching the SAME order is
     * idempotent-ok; a DIFFERENT order already attached is a typed conflict.
     * The check-then-set runs inside this transactional method (the V30 mapper
     * has no conditional update) — order is the only writer of this field.
     */
    public LitemallPromotionOperationResult attachOrder(LitemallCombinationPinkId pinkId,
                                                        LitemallUserId userId, Integer orderId) {
        if (orderId == null) {
            return LitemallPromotionOperationResult.groupOrderAttachFailed("orderId is required");
        }
        Optional<LitemallCombinationPinkAggregate> slotOpt = pinkRepository.findById(pinkId);
        if (slotOpt.isEmpty()) {
            return LitemallPromotionOperationResult.groupOrderAttachFailed("Group slot not found");
        }
        LitemallCombinationPinkAggregate slot = slotOpt.get();

        if (orderId.equals(slot.getOrderId())) {
            // Idempotent replay — already linked to this very order.
            return LitemallPromotionOperationResult.groupOrderAttached(
                    attachData(slot, false));
        }
        if (slot.getOrderId() != null) {
            return LitemallPromotionOperationResult.groupOrderAttachFailed(
                    "Slot is already attached to order " + slot.getOrderId());
        }
        if (userId != null && !slot.isOwnedBy(userId)) {
            return LitemallPromotionOperationResult.groupOrderAttachFailed(
                    "Slot does not belong to this user");
        }
        if (slot.isReleasedOrFailed()) {
            return LitemallPromotionOperationResult.groupOrderAttachFailed(
                    "Group slot already failed — the group expired or the slot was released");
        }

        slot.setOrderId(orderId);
        pinkRepository.update(slot);
        logger.info("Attached order {} to group slot {}", orderId, pinkId.getId());
        return LitemallPromotionOperationResult.groupOrderAttached(attachData(slot, true));
    }

    private Map<String, Object> attachData(LitemallCombinationPinkAggregate slot, boolean attached) {
        Map<String, Object> data = new HashMap<>();
        data.put("pinkId", slot.getPinkId().getId());
        data.put("orderId", slot.getOrderId());
        data.put("status", slot.getStatus() != null ? slot.getStatus().getDisplayName() : null);
        data.put("attached", attached);
        return data;
    }

    /**
     * Order frees a slot when its order is cancelled BEFORE the group settles.
     * Semantics (documented in {@code docs/spec-groupon-priced-submit-contract.md}):
     * <ul>
     * <li>Only a PENDING group can release. The released slot is flipped to
     *     FAILED and stops counting toward the headcount (the seat becomes
     *     joinable again); the row is kept for audit/idempotency.</li>
     * <li>Releasing the LEADER slot dissolves the whole group: every still
     *     pending slot fails and a {@code GROUP_EXPIRED} event is emitted with
     *     the failed slot ids ({@code memberPinkIds}), so other members' paid
     *     orders ride the existing order-side auto-cancel/refund listener —
     *     exactly the expiry-sweep path.</li>
     * <li>Idempotent: unknown pinkId, or a slot already FAILED whose recorded
     *     orderId matches (or was never attached), returns ok.</li>
     * <li>A SUCCESS group (or a still-active slot of an already settled group)
     *     is a typed "too late" refusal — completed groups are handled by the
     *     aftersale/refund path, never unwound here.</li>
     * </ul>
     */
    public LitemallPromotionOperationResult releaseSlot(LitemallCombinationPinkId pinkId,
                                                        LitemallUserId userId, Integer orderId) {
        Optional<LitemallCombinationPinkAggregate> slotOpt = pinkRepository.findById(pinkId);
        if (slotOpt.isEmpty()) {
            // Idempotent: nothing to free.
            Map<String, Object> data = new HashMap<>();
            data.put("pinkId", pinkId.getId());
            data.put("released", false);
            data.put("alreadyReleased", true);
            return LitemallPromotionOperationResult.groupSlotReleased(data);
        }
        LitemallCombinationPinkAggregate slot = slotOpt.get();

        boolean orderMatches = slot.getOrderId() == null || slot.getOrderId().equals(orderId);
        if (slot.isReleasedOrFailed()) {
            if (orderMatches) {
                // Already released (or the whole group already failed) — moot.
                Map<String, Object> data = new HashMap<>();
                data.put("pinkId", pinkId.getId());
                data.put("released", false);
                data.put("alreadyReleased", true);
                return LitemallPromotionOperationResult.groupSlotReleased(data);
            }
            return LitemallPromotionOperationResult.groupSlotReleaseFailed(
                    "Slot is attached to order " + slot.getOrderId() + ", not " + orderId);
        }
        if (!orderMatches) {
            return LitemallPromotionOperationResult.groupSlotReleaseFailed(
                    "Slot is attached to order " + slot.getOrderId() + ", not " + orderId);
        }
        if (userId != null && !slot.isOwnedBy(userId)) {
            return LitemallPromotionOperationResult.groupSlotReleaseFailed(
                    "Slot does not belong to this user");
        }

        LitemallCombinationPinkAggregate leader = slot.isLeader()
                ? slot
                : pinkRepository.findById(slot.getHeadId()).orElse(null);
        if (leader == null || !leader.isPending() || !slot.isPending()) {
            // Group already settled (Success, or an inconsistent settled state).
            return LitemallPromotionOperationResult.groupSlotReleaseFailed(
                    "Too late — the group has already completed or failed");
        }

        if (slot.isLeader()) {
            // Leader release dissolves the group: fail every pending slot and
            // emit GROUP_EXPIRED, the same signal the expiry sweep sends, so
            // order's listener cancels/refunds the other members' paid orders.
            int memberCount = 0;
            List<Integer> memberPinkIds = new ArrayList<>();
            for (LitemallCombinationPinkAggregate s : pinkRepository.findGroup(leader.getPinkId())) {
                if (s.isPending()) {
                    s.fail();
                    pinkRepository.update(s);
                    memberPinkIds.add(s.getPinkId().getId());
                }
                memberCount++;
            }
            domainEventPublisher.publish(new LitemallGroupExpiredEvent(
                    leader.getPinkId(), leader.getCombinationId(), memberCount, memberPinkIds));
            logger.info("Leader slot {} released — group dissolved ({} slot(s) failed)",
                    pinkId.getId(), memberPinkIds.size());

            Map<String, Object> data = new HashMap<>();
            data.put("pinkId", pinkId.getId());
            data.put("released", true);
            data.put("groupDissolved", true);
            data.put("memberPinkIds", memberPinkIds);
            return LitemallPromotionOperationResult.groupSlotReleased(data);
        }

        slot.fail();
        pinkRepository.update(slot);
        long remaining = pinkRepository.findGroup(leader.getPinkId()).stream()
                .filter(s -> !s.isReleasedOrFailed())
                .count();
        logger.info("Released member slot {} of group {} ({} active member(s) remain)",
                pinkId.getId(), leader.getPinkId().getId(), remaining);

        Map<String, Object> data = new HashMap<>();
        data.put("pinkId", pinkId.getId());
        data.put("released", true);
        data.put("groupDissolved", false);
        data.put("memberCount", (int) remaining);
        return LitemallPromotionOperationResult.groupSlotReleased(data);
    }

    // ----- participation read models -----

    @Transactional(readOnly = true)
    public List<LitemallCombinationPinkAggregate> getMyGroups(LitemallUserId userId) {
        return pinkRepository.findByUser(userId);
    }

    @Transactional(readOnly = true)
    public List<LitemallCombinationPinkAggregate> getGroup(LitemallCombinationPinkId headId) {
        return pinkRepository.findGroup(headId);
    }

    @Transactional(readOnly = true)
    public Optional<LitemallCombinationPinkAggregate> getPink(LitemallCombinationPinkId pinkId) {
        return pinkRepository.findById(pinkId);
    }

    @Transactional(readOnly = true)
    public List<LitemallCombinationPinkAggregate> listGroups(LitemallCombinationId combinationId,
                                                             LitemallCombinationPinkStatus status) {
        return pinkRepository.findLeaders(combinationId, status);
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
