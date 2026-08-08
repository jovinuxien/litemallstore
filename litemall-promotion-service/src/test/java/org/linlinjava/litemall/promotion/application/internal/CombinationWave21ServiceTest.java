package org.linlinjava.litemall.promotion.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.promotion.domain.events.combination.LitemallGroupCompletedEvent;
import org.linlinjava.litemall.promotion.domain.events.combination.LitemallGroupExpiredEvent;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCombinationAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCombinationPinkAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallJoinGroupCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallStartGroupCommand;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave-21 order-linkage follow-ups against in-memory fakes: attach-order CAS
 * (null-then-set, same-order idempotency, different-order conflict, unknown
 * pink), slot release (pending ok + idempotent, freed seat joinable again,
 * settled group refused, leader release dissolves the group via GROUP_EXPIRED)
 * and the additive {@code memberPinkIds[]} on GROUP_COMPLETED / GROUP_EXPIRED.
 */
class CombinationWave21ServiceTest {

    // ---- fakes -------------------------------------------------------

    private static class FakeCombinationRepository implements LitemallCombinationRepository {
        final Map<Integer, LitemallCombinationAggregate> rows = new HashMap<>();
        final AtomicInteger sequence = new AtomicInteger(10);

        @Override
        public Optional<LitemallCombinationAggregate> findById(LitemallCombinationId combinationId) {
            return Optional.ofNullable(rows.get(combinationId.getId()));
        }

        @Override
        public List<LitemallCombinationAggregate> findActive() {
            return rows.values().stream()
                    .filter(c -> LitemallCombinationStatus.ACTIVE.equals(c.getStatus()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<LitemallCombinationAggregate> findAll() {
            return new ArrayList<>(rows.values());
        }

        @Override
        public void save(LitemallCombinationAggregate combination) {
            if (combination.getCombinationId() == null) {
                combination.setCombinationId(new LitemallCombinationId(sequence.incrementAndGet()));
            }
            rows.put(combination.getCombinationId().getId(), combination);
        }

        @Override
        public void delete(LitemallCombinationId combinationId) {
            rows.remove(combinationId.getId());
        }
    }

    private static class FakePinkRepository implements LitemallCombinationPinkRepository {
        final Map<Integer, LitemallCombinationPinkAggregate> rows = new HashMap<>();
        final AtomicInteger sequence = new AtomicInteger(100);

        @Override
        public Optional<LitemallCombinationPinkAggregate> findById(LitemallCombinationPinkId pinkId) {
            return Optional.ofNullable(rows.get(pinkId.getId()));
        }

        @Override
        public List<LitemallCombinationPinkAggregate> findGroup(LitemallCombinationPinkId headId) {
            int head = headId.getId();
            return rows.values().stream()
                    .filter(p -> p.getPinkId().getId() == head
                            || (p.getHeadId() != null && p.getHeadId().getId() == head))
                    .sorted((a, b) -> Integer.compare(a.getPinkId().getId(), b.getPinkId().getId()))
                    .collect(Collectors.toList());
        }

        @Override
        public int countGroup(LitemallCombinationPinkId headId) {
            return findGroup(headId).size();
        }

        @Override
        public List<LitemallCombinationPinkAggregate> findByUser(LitemallUserId userId) {
            return rows.values().stream()
                    .filter(p -> p.isOwnedBy(userId))
                    .collect(Collectors.toList());
        }

        @Override
        public List<LitemallCombinationPinkAggregate> findExpiredPendingLeaders(LocalDateTime now) {
            return rows.values().stream()
                    .filter(LitemallCombinationPinkAggregate::isLeader)
                    .filter(LitemallCombinationPinkAggregate::isPending)
                    .filter(p -> p.isExpired(now))
                    .collect(Collectors.toList());
        }

        @Override
        public List<LitemallCombinationPinkAggregate> findLeaders(
                LitemallCombinationId combinationId, LitemallCombinationPinkStatus status) {
            return rows.values().stream()
                    .filter(LitemallCombinationPinkAggregate::isLeader)
                    .filter(p -> combinationId == null
                            || p.getCombinationId().getId().equals(combinationId.getId()))
                    .filter(p -> status == null || status.equals(p.getStatus()))
                    .collect(Collectors.toList());
        }

        @Override
        public void add(LitemallCombinationPinkAggregate pink) {
            pink.setPinkId(new LitemallCombinationPinkId(sequence.incrementAndGet()));
            rows.put(pink.getPinkId().getId(), pink);
        }

        @Override
        public void update(LitemallCombinationPinkAggregate pink) {
            rows.put(pink.getPinkId().getId(), pink);
        }
    }

    private static class CapturingPublisher implements LitemallDomainEventPublisher {
        final List<LitemallDomainEvent> events = new ArrayList<>();

        @Override
        public void publish(LitemallDomainEvent event) {
            events.add(event);
        }

        <T> List<T> ofType(Class<T> type) {
            return events.stream().filter(type::isInstance).map(type::cast)
                    .collect(Collectors.toList());
        }
    }

    // ---- setup -------------------------------------------------------

    private FakeCombinationRepository combinationRepository;
    private FakePinkRepository pinkRepository;
    private CapturingPublisher publisher;
    private LitemallCombinationServiceImpl service;
    private LitemallCombinationId campaignId;

    private static final LitemallUserId LEADER_USER = new LitemallUserId(1);
    private static final LitemallUserId MEMBER_USER = new LitemallUserId(2);
    private static final LitemallUserId THIRD_USER = new LitemallUserId(3);

    @BeforeEach
    void setUp() {
        combinationRepository = new FakeCombinationRepository();
        pinkRepository = new FakePinkRepository();
        publisher = new CapturingPublisher();
        service = new LitemallCombinationServiceImpl(combinationRepository, pinkRepository,
                new LitemallCombinationDomainService(), publisher, new PromotionGrouponProperties());

        LitemallCombinationAggregate campaign = LitemallCombinationAggregate.builder()
                .goodsId(10008302)
                .title("Group buy")
                .combinationPrice(new LitemallMoney(new BigDecimal("9.99")))
                .originalPrice(new LitemallMoney(new BigDecimal("19.99")))
                .requiredMembers(3)
                .limitPerUser(1)
                .startTime(LocalDateTime.now().minusDays(1))
                .endTime(LocalDateTime.now().plusDays(7))
                .status(LitemallCombinationStatus.ACTIVE)
                .build();
        combinationRepository.save(campaign);
        campaignId = campaign.getCombinationId();
    }

    private LitemallCombinationPinkId startGroup(LitemallUserId user) {
        LitemallPromotionOperationResult result =
                service.startGroup(new LitemallStartGroupCommand(user, campaignId));
        assertTrue(result.isSuccess(), result.getMessage());
        return new LitemallCombinationPinkId((Integer) result.getData().get("pinkId"));
    }

    private LitemallPromotionOperationResult join(LitemallCombinationPinkId leaderId,
                                                  LitemallUserId user) {
        return service.joinGroup(new LitemallJoinGroupCommand(user, leaderId));
    }

    private LitemallCombinationPinkAggregate slot(LitemallCombinationPinkId id) {
        return pinkRepository.rows.get(id.getId());
    }

    // ---- attach-order CAS --------------------------------------------

    @Test
    void attachSetsOrderIdWhenNull() {
        LitemallCombinationPinkId leaderId = startGroup(LEADER_USER);
        assertNull(slot(leaderId).getOrderId());

        LitemallPromotionOperationResult result = service.attachOrder(leaderId, LEADER_USER, 500);

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(true, result.getData().get("attached"));
        assertEquals(500, slot(leaderId).getOrderId());
    }

    @Test
    void attachSameOrderIsIdempotent() {
        LitemallCombinationPinkId leaderId = startGroup(LEADER_USER);
        service.attachOrder(leaderId, LEADER_USER, 500);

        LitemallPromotionOperationResult replay = service.attachOrder(leaderId, LEADER_USER, 500);

        assertTrue(replay.isSuccess(), replay.getMessage());
        assertEquals(false, replay.getData().get("attached"));
        assertEquals(500, slot(leaderId).getOrderId());
    }

    @Test
    void attachDifferentOrderIsTypedConflict() {
        LitemallCombinationPinkId leaderId = startGroup(LEADER_USER);
        service.attachOrder(leaderId, LEADER_USER, 500);

        LitemallPromotionOperationResult conflict = service.attachOrder(leaderId, LEADER_USER, 501);

        assertFalse(conflict.isSuccess());
        assertTrue(conflict.getMessage().contains("already attached to order 500"),
                conflict.getMessage());
        assertEquals(500, slot(leaderId).getOrderId());
    }

    @Test
    void attachUnknownPinkIsTypedNotFound() {
        LitemallPromotionOperationResult result =
                service.attachOrder(new LitemallCombinationPinkId(9999), LEADER_USER, 500);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Group slot not found"), result.getMessage());
    }

    @Test
    void attachByNonOwnerIsRefused() {
        LitemallCombinationPinkId leaderId = startGroup(LEADER_USER);

        LitemallPromotionOperationResult result = service.attachOrder(leaderId, MEMBER_USER, 500);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("does not belong"), result.getMessage());
        assertNull(slot(leaderId).getOrderId());
    }

    // ---- slot release ------------------------------------------------

    @Test
    void releasePendingMemberFreesSeatAndIsIdempotent() {
        LitemallCombinationPinkId leaderId = startGroup(LEADER_USER);
        LitemallPromotionOperationResult joined = join(leaderId, MEMBER_USER);
        LitemallCombinationPinkId memberId =
                new LitemallCombinationPinkId((Integer) joined.getData().get("pinkId"));
        service.attachOrder(memberId, MEMBER_USER, 700);

        LitemallPromotionOperationResult released = service.releaseSlot(memberId, MEMBER_USER, 700);
        assertTrue(released.isSuccess(), released.getMessage());
        assertEquals(true, released.getData().get("released"));
        assertEquals(false, released.getData().get("groupDissolved"));
        assertEquals(1, released.getData().get("memberCount"));
        assertEquals(LitemallCombinationPinkStatus.FAILED, slot(memberId).getStatus());
        assertTrue(slot(leaderId).isPending());

        // Replay with the same orderId is idempotent-ok.
        LitemallPromotionOperationResult replay = service.releaseSlot(memberId, MEMBER_USER, 700);
        assertTrue(replay.isSuccess(), replay.getMessage());
        assertEquals(true, replay.getData().get("alreadyReleased"));

        // The freed seat is joinable again — including by the releasing user —
        // and the group still completes at the required headcount.
        assertTrue(join(leaderId, THIRD_USER).isSuccess());
        LitemallPromotionOperationResult rejoin = join(leaderId, MEMBER_USER);
        assertTrue(rejoin.isSuccess(), rejoin.getMessage());
        assertEquals(true, rejoin.getData().get("completed"));
    }

    @Test
    void releaseUnknownPinkIsIdempotentOk() {
        LitemallPromotionOperationResult result =
                service.releaseSlot(new LitemallCombinationPinkId(9999), MEMBER_USER, 700);

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(true, result.getData().get("alreadyReleased"));
    }

    @Test
    void releaseWithDifferentOrderIsTypedConflict() {
        LitemallCombinationPinkId leaderId = startGroup(LEADER_USER);
        service.attachOrder(leaderId, LEADER_USER, 500);

        LitemallPromotionOperationResult result = service.releaseSlot(leaderId, LEADER_USER, 501);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("order 500"), result.getMessage());
        assertTrue(slot(leaderId).isPending());
    }

    @Test
    void releaseOnCompletedGroupIsTypedRefusal() {
        LitemallCombinationPinkId leaderId = startGroup(LEADER_USER);
        join(leaderId, MEMBER_USER);
        LitemallPromotionOperationResult completing = join(leaderId, THIRD_USER);
        assertEquals(true, completing.getData().get("completed"));
        LitemallCombinationPinkId memberId =
                new LitemallCombinationPinkId((Integer) completing.getData().get("pinkId"));

        LitemallPromotionOperationResult result = service.releaseSlot(memberId, THIRD_USER, 700);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Too late"), result.getMessage());
        assertEquals(LitemallCombinationPinkStatus.SUCCESS, slot(memberId).getStatus());
    }

    @Test
    void leaderReleaseDissolvesPendingGroupAndEmitsGroupExpired() {
        LitemallCombinationPinkId leaderId = startGroup(LEADER_USER);
        LitemallPromotionOperationResult joined = join(leaderId, MEMBER_USER);
        LitemallCombinationPinkId memberId =
                new LitemallCombinationPinkId((Integer) joined.getData().get("pinkId"));
        service.attachOrder(leaderId, LEADER_USER, 500);

        LitemallPromotionOperationResult result = service.releaseSlot(leaderId, LEADER_USER, 500);

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(true, result.getData().get("groupDissolved"));
        assertEquals(LitemallCombinationPinkStatus.FAILED, slot(leaderId).getStatus());
        assertEquals(LitemallCombinationPinkStatus.FAILED, slot(memberId).getStatus());

        List<LitemallGroupExpiredEvent> expired = publisher.ofType(LitemallGroupExpiredEvent.class);
        assertEquals(1, expired.size());
        assertEquals(leaderId.getId(), expired.get(0).getGroupPinkId().getId());
        assertEquals(List.of(leaderId.getId(), memberId.getId()),
                expired.get(0).getMemberPinkIds());

        // Retrying the leader release after dissolution is idempotent-ok.
        LitemallPromotionOperationResult replay = service.releaseSlot(leaderId, LEADER_USER, 500);
        assertTrue(replay.isSuccess(), replay.getMessage());
        assertEquals(true, replay.getData().get("alreadyReleased"));
    }

    // ---- event payloads ----------------------------------------------

    @Test
    void groupCompletedEventCarriesMemberPinkIds() {
        LitemallCombinationPinkId leaderId = startGroup(LEADER_USER);
        LitemallCombinationPinkId secondId = new LitemallCombinationPinkId(
                (Integer) join(leaderId, MEMBER_USER).getData().get("pinkId"));
        LitemallCombinationPinkId thirdId = new LitemallCombinationPinkId(
                (Integer) join(leaderId, THIRD_USER).getData().get("pinkId"));

        List<LitemallGroupCompletedEvent> completed =
                publisher.ofType(LitemallGroupCompletedEvent.class);
        assertEquals(1, completed.size());
        assertEquals(3, completed.get(0).getMemberCount());
        assertEquals(List.of(leaderId.getId(), secondId.getId(), thirdId.getId()),
                completed.get(0).getMemberPinkIds());
    }

    @Test
    void expirySweepListsOnlySlotsFailedNow() {
        LitemallCombinationPinkId leaderId = startGroup(LEADER_USER);
        LitemallCombinationPinkId memberId = new LitemallCombinationPinkId(
                (Integer) join(leaderId, MEMBER_USER).getData().get("pinkId"));
        // Member released before expiry — its order was handled at release time.
        assertTrue(service.releaseSlot(memberId, MEMBER_USER, 700).isSuccess());

        slot(leaderId).setExpireTime(LocalDateTime.now().minusMinutes(5));
        int failedGroups = service.expireOverdue();

        assertEquals(1, failedGroups);
        List<LitemallGroupExpiredEvent> expired = publisher.ofType(LitemallGroupExpiredEvent.class);
        assertEquals(1, expired.size());
        assertEquals(List.of(leaderId.getId()), expired.get(0).getMemberPinkIds());
        assertEquals(LitemallCombinationPinkStatus.FAILED, slot(leaderId).getStatus());
    }
}
