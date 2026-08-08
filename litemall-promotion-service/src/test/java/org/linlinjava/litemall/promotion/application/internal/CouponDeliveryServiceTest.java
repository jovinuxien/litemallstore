package org.linlinjava.litemall.promotion.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallCouponDelivery;
import org.linlinjava.litemall.promotion.application.internal.CouponDeliveryServiceImpl.CouponDeliveryException;
import org.linlinjava.litemall.promotion.application.internal.CouponDeliveryServiceImpl.DeliverResult;
import org.linlinjava.litemall.promotion.application.internal.CouponDeliveryServiceImpl.PerformanceView;
import org.linlinjava.litemall.promotion.application.internal.CouponDeliveryServiceImpl.SegmentCriteria;
import org.linlinjava.litemall.promotion.application.ports.CouponMarginBasisPort;
import org.linlinjava.litemall.promotion.application.ports.CouponMarginBasisPort.MarginBasis;
import org.linlinjava.litemall.promotion.application.ports.CouponScopePort;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallUserCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallCouponDeliveryRepository;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallCouponDeliveryRepository.CouponPerformance;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallCouponDeliveryRepository.DeliveryPage;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallCouponRepository;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallUserCouponRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponStatus;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponTimeType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallUserCouponStatus;
import org.linlinjava.litemall.promotion.domain.service.LitemallCouponDomainService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave-22 delivery/measurement semantics against in-memory fakes (the segment
 * SQL itself — paid-status set, criterion composition — is covered by
 * litemall-db's {@code CouponDeliveryMapperTest} on real MySQL): criteria
 * validation (typed 402), preview zero-side-effect, grants through the
 * EXISTING direct-grant path with per-user-limit idempotency (skipped counts),
 * grant-path refusals counted as skipped, typed refusals before any grant,
 * ledger row written, sweep cap refusal, and performance null-not-zero math.
 */
class CouponDeliveryServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 8, 12, 0);
    private static final int COUPON = 500;
    private static final int CAP = 5;

    // ---- fakes -------------------------------------------------------

    private static class FakeCouponRepository implements LitemallCouponRepository {
        final Map<Integer, LitemallCouponAggregate> rows = new HashMap<>();

        @Override
        public Optional<LitemallCouponAggregate> findById(LitemallCouponId couponId) {
            return Optional.ofNullable(rows.get(couponId.getId()));
        }

        @Override
        public List<LitemallCouponAggregate> findReceivable() {
            return new ArrayList<>(rows.values());
        }

        @Override
        public Optional<LitemallCouponAggregate> findByCode(String code) {
            return Optional.empty();
        }

        @Override
        public List<LitemallCouponAggregate> findAll(int page, int limit) {
            return new ArrayList<>(rows.values());
        }

        @Override
        public void save(LitemallCouponAggregate coupon) {
            rows.put(coupon.getCouponId().getId(), coupon);
        }

        @Override
        public void delete(LitemallCouponId couponId) {
            rows.remove(couponId.getId());
        }
    }

    private static class FakeUserCouponRepository implements LitemallUserCouponRepository {
        final Map<Integer, LitemallUserCouponAggregate> rows = new HashMap<>();
        final AtomicInteger sequence = new AtomicInteger(1000);

        @Override
        public Optional<LitemallUserCouponAggregate> findById(LitemallUserCouponId id) {
            return Optional.ofNullable(rows.get(id.getId()));
        }

        @Override
        public int countByUserAndCoupon(LitemallUserId userId, LitemallCouponId couponId) {
            return (int) rows.values().stream()
                    .filter(h -> h.getUserId().getId().equals(userId.getId())
                            && h.getCouponId().getId().equals(couponId.getId()))
                    .count();
        }

        @Override
        public int countByCoupon(LitemallCouponId couponId) {
            return (int) rows.values().stream()
                    .filter(h -> h.getCouponId().getId().equals(couponId.getId()))
                    .count();
        }

        @Override
        public List<LitemallUserCouponAggregate> findUsableByUser(LitemallUserId userId) {
            return rows.values().stream()
                    .filter(h -> h.getUserId().getId().equals(userId.getId()) && h.isUsable())
                    .toList();
        }

        @Override
        public List<LitemallUserCouponAggregate> findByUser(LitemallUserId userId,
                                                            LitemallUserCouponStatus status) {
            return rows.values().stream()
                    .filter(h -> h.getUserId().getId().equals(userId.getId()))
                    .filter(h -> status == null || status.equals(h.getStatus()))
                    .toList();
        }

        @Override
        public List<LitemallUserCouponAggregate> findByCoupon(LitemallCouponId couponId,
                                                              int page, int limit) {
            return rows.values().stream()
                    .filter(h -> h.getCouponId().getId().equals(couponId.getId()))
                    .toList();
        }

        @Override
        public void add(LitemallUserCouponAggregate userCoupon) {
            if (userCoupon.getUserCouponId() == null) {
                userCoupon.setUserCouponId(new LitemallUserCouponId(sequence.incrementAndGet()));
            }
            rows.put(userCoupon.getUserCouponId().getId(), userCoupon);
        }

        @Override
        public void update(LitemallUserCouponAggregate userCoupon) {
            rows.put(userCoupon.getUserCouponId().getId(), userCoupon);
        }
    }

    private static class FakeDeliveryRepository implements LitemallCouponDeliveryRepository {
        List<Integer> audience = List.of();
        int audienceQueries = 0;
        LocalDateTime lastPaidSince;
        Integer lastMinFrequency;
        BigDecimal lastMinMonetary;
        int lastLimit;
        final List<LitemallCouponDelivery> ledger = new ArrayList<>();
        CouponPerformance perf = new CouponPerformance(0, 0, 0, BigDecimal.ZERO);

        @Override
        public List<Integer> selectPaidAudience(LocalDateTime paidSince, Integer minFrequency,
                                                BigDecimal minMonetary, int limit) {
            audienceQueries++;
            lastPaidSince = paidSince;
            lastMinFrequency = minFrequency;
            lastMinMonetary = minMonetary;
            lastLimit = limit;
            return audience.subList(0, Math.min(audience.size(), limit));
        }

        @Override
        public void add(LitemallCouponDelivery delivery) {
            delivery.setId(ledger.size() + 1);
            ledger.add(delivery);
        }

        @Override
        public DeliveryPage findDeliveries(Integer couponId, int page, int limit) {
            List<LitemallCouponDelivery> filtered = ledger.stream()
                    .filter(d -> couponId == null || couponId.equals(d.getCouponId()))
                    .toList();
            return new DeliveryPage(filtered.size(), filtered);
        }

        @Override
        public CouponPerformance performance(int couponId) {
            return perf;
        }
    }

    private static final CouponScopePort SCOPE = (goodsIds, categoryIds) -> List.of();
    private static final CouponMarginBasisPort BASIS = (goodsIds, categoryIds) ->
            new MarginBasis(100, 100, 0, new BigDecimal("0.5"), new BigDecimal("4.99"));

    private FakeCouponRepository couponRepository;
    private FakeUserCouponRepository userCouponRepository;
    private FakeDeliveryRepository deliveryRepository;
    private CouponDeliveryServiceImpl service;

    @BeforeEach
    void setUp() {
        couponRepository = new FakeCouponRepository();
        userCouponRepository = new FakeUserCouponRepository();
        deliveryRepository = new FakeDeliveryRepository();
        LitemallCouponServiceImpl couponService = new LitemallCouponServiceImpl(
                couponRepository, userCouponRepository,
                new LitemallCouponDomainService(), event -> { },
                new CouponMarginGuard(BASIS, new BigDecimal("1.05")), SCOPE);
        service = new CouponDeliveryServiceImpl(couponRepository, userCouponRepository,
                couponService, deliveryRepository, CAP);
    }

    private LitemallCouponAggregate.LitemallCouponAggregateBuilder coupon() {
        return LitemallCouponAggregate.builder()
                .couponId(new LitemallCouponId(COUPON))
                .name("Delivery test")
                .discount(new LitemallMoney(new BigDecimal("5.00")))
                .min(new LitemallMoney(new BigDecimal("50.00")))
                .total(0)
                .limitPerUser(1)
                .type(LitemallCouponType.COMMON)
                .status(LitemallCouponStatus.NORMAL)
                .timeType(LitemallCouponTimeType.DAYS)
                .days(30);
    }

    private void putCoupon(LitemallCouponAggregate.LitemallCouponAggregateBuilder builder) {
        LitemallCouponAggregate c = builder.build();
        couponRepository.rows.put(c.getCouponId().getId(), c);
    }

    private DeliverResult deliver(SegmentCriteria criteria, boolean preview) {
        return service.deliver(COUPON, criteria, preview, NOW);
    }

    // ---- criteria validation -----------------------------------------

    @Test
    void noCriterion_typed402() {
        putCoupon(coupon());
        CouponDeliveryException e = assertThrows(CouponDeliveryException.class,
                () -> deliver(new SegmentCriteria(null, null, null), false));
        assertEquals(CouponDeliveryServiceImpl.ERRNO_BAD_PARAM, e.getErrno());
        assertEquals(0, deliveryRepository.audienceQueries);
        assertTrue(deliveryRepository.ledger.isEmpty());
    }

    @Test
    void invalidCriterionValues_typed402() {
        putCoupon(coupon());
        assertEquals(CouponDeliveryServiceImpl.ERRNO_BAD_PARAM,
                assertThrows(CouponDeliveryException.class,
                        () -> deliver(new SegmentCriteria(0, null, null), false)).getErrno());
        assertEquals(CouponDeliveryServiceImpl.ERRNO_BAD_PARAM,
                assertThrows(CouponDeliveryException.class,
                        () -> deliver(new SegmentCriteria(null, 0, null), false)).getErrno());
        assertEquals(CouponDeliveryServiceImpl.ERRNO_BAD_PARAM,
                assertThrows(CouponDeliveryException.class,
                        () -> deliver(new SegmentCriteria(null, null, BigDecimal.ZERO), false))
                        .getErrno());
    }

    // ---- criteria pass-through to the segment query -------------------

    @Test
    void criteriaFlowIntoAudienceQuery_recencyBecomesPaidSince() {
        putCoupon(coupon());
        deliveryRepository.audience = List.of(7);
        deliver(new SegmentCriteria(30, 2, new BigDecimal("100.00")), true);
        assertEquals(NOW.minusDays(30), deliveryRepository.lastPaidSince);
        assertEquals(2, deliveryRepository.lastMinFrequency);
        assertEquals(new BigDecimal("100.00"), deliveryRepository.lastMinMonetary);
        assertEquals(CAP + 1, deliveryRepository.lastLimit, "cap+1 to detect overflow");
    }

    // ---- preview ------------------------------------------------------

    @Test
    void previewCountsOnly_zeroSideEffects() {
        putCoupon(coupon());
        deliveryRepository.audience = List.of(1, 2, 3);
        DeliverResult result = deliver(new SegmentCriteria(30, null, null), true);
        assertEquals(3, result.matched());
        assertNull(result.granted());
        assertNull(result.skipped());
        assertTrue(userCouponRepository.rows.isEmpty(), "preview must grant nothing");
        assertTrue(deliveryRepository.ledger.isEmpty(), "preview must write no ledger row");
    }

    // ---- real run -----------------------------------------------------

    @Test
    void deliverGrantsEachMatchedUser_andWritesOneLedgerRow() {
        putCoupon(coupon());
        deliveryRepository.audience = List.of(1, 2, 3);
        DeliverResult result = deliver(new SegmentCriteria(30, null, new BigDecimal("50")), false);
        assertEquals(3, result.matched());
        assertEquals(3, result.granted());
        assertEquals(0, result.skipped());
        assertEquals(3, userCouponRepository.rows.size());
        assertTrue(userCouponRepository.rows.values().stream()
                .allMatch(h -> h.getStatus() == LitemallUserCouponStatus.USABLE
                        && h.getCouponId().getId() == COUPON));

        assertEquals(1, deliveryRepository.ledger.size());
        LitemallCouponDelivery row = deliveryRepository.ledger.get(0);
        assertEquals(COUPON, row.getCouponId());
        assertEquals(3, row.getMatched());
        assertEquals(3, row.getGranted());
        assertEquals(0, row.getSkipped());
        assertEquals("{\"recencyDays\":30,\"minMonetary\":50}", row.getSegmentJson());
    }

    @Test
    void redeliverIsIdempotent_alreadyAtLimitCountsAsSkipped() {
        putCoupon(coupon());
        deliveryRepository.audience = List.of(1, 2, 3);
        deliver(new SegmentCriteria(30, null, null), false);
        DeliverResult second = deliver(new SegmentCriteria(30, null, null), false);
        assertEquals(3, second.matched());
        assertEquals(0, second.granted());
        assertEquals(3, second.skipped());
        assertEquals(3, userCouponRepository.rows.size(), "no double-grants");
        assertEquals(2, deliveryRepository.ledger.size());
        assertEquals(3, deliveryRepository.ledger.get(1).getSkipped());
    }

    @Test
    void perUserLimitAboveOne_userBelowLimitStillGranted() {
        putCoupon(coupon().limitPerUser(2));
        deliveryRepository.audience = List.of(1);
        deliver(new SegmentCriteria(7, null, null), false);
        DeliverResult second = deliver(new SegmentCriteria(7, null, null), false);
        assertEquals(1, second.granted(), "second grant allowed below limit 2");
        DeliverResult third = deliver(new SegmentCriteria(7, null, null), false);
        assertEquals(0, third.granted());
        assertEquals(1, third.skipped());
        assertEquals(2, userCouponRepository.rows.size());
    }

    @Test
    void grantPathRefusal_totalSupplyExhausted_countsAsSkipped() {
        putCoupon(coupon().total(2));
        deliveryRepository.audience = List.of(1, 2, 3);
        DeliverResult result = deliver(new SegmentCriteria(30, null, null), false);
        assertEquals(3, result.matched());
        assertEquals(2, result.granted(), "existing grant path enforces total supply");
        assertEquals(1, result.skipped());
        assertEquals(2, userCouponRepository.rows.size());
    }

    // ---- typed refusals BEFORE any grant ------------------------------

    @Test
    void unknownCoupon_typed770_beforeAnyQuery() {
        CouponDeliveryException e = assertThrows(CouponDeliveryException.class,
                () -> deliver(new SegmentCriteria(30, null, null), false));
        assertEquals(CouponDeliveryServiceImpl.ERRNO_COUPON_NOT_FOUND, e.getErrno());
        assertEquals(0, deliveryRepository.audienceQueries);
    }

    @Test
    void withdrawnCoupon_typed771_nothingGranted() {
        putCoupon(coupon().status(LitemallCouponStatus.OUT));
        deliveryRepository.audience = List.of(1, 2);
        CouponDeliveryException e = assertThrows(CouponDeliveryException.class,
                () -> deliver(new SegmentCriteria(30, null, null), false));
        assertEquals(CouponDeliveryServiceImpl.ERRNO_COUPON_NOT_DELIVERABLE, e.getErrno());
        assertEquals(0, deliveryRepository.audienceQueries);
        assertTrue(userCouponRepository.rows.isEmpty());
        assertTrue(deliveryRepository.ledger.isEmpty());
    }

    @Test
    void expiredAbsoluteWindowCoupon_typed771() {
        putCoupon(coupon()
                .timeType(LitemallCouponTimeType.TIME)
                .days(null)
                .startTime(NOW.minusDays(20))
                .endTime(NOW.minusDays(1)));
        CouponDeliveryException e = assertThrows(CouponDeliveryException.class,
                () -> deliver(new SegmentCriteria(30, null, null), false));
        assertEquals(CouponDeliveryServiceImpl.ERRNO_COUPON_NOT_DELIVERABLE, e.getErrno());
        assertTrue(userCouponRepository.rows.isEmpty());
    }

    // ---- sweep cap ----------------------------------------------------

    @Test
    void audienceAboveCap_typed772_noGrantsNoLedger() {
        putCoupon(coupon());
        deliveryRepository.audience = List.of(1, 2, 3, 4, 5, 6); // CAP is 5
        CouponDeliveryException e = assertThrows(CouponDeliveryException.class,
                () -> deliver(new SegmentCriteria(30, null, null), false));
        assertEquals(CouponDeliveryServiceImpl.ERRNO_AUDIENCE_TOO_LARGE, e.getErrno());
        assertTrue(e.getMessage().contains(String.valueOf(CAP)), e.getMessage());
        assertTrue(userCouponRepository.rows.isEmpty());
        assertTrue(deliveryRepository.ledger.isEmpty());
    }

    @Test
    void audienceExactlyAtCap_delivers() {
        putCoupon(coupon());
        deliveryRepository.audience = List.of(1, 2, 3, 4, 5);
        DeliverResult result = deliver(new SegmentCriteria(30, null, null), false);
        assertEquals(5, result.matched());
        assertEquals(5, result.granted());
    }

    // ---- performance math ---------------------------------------------

    @Test
    void performance_normalMath_oneDpPctTwoDpMoney() {
        putCoupon(coupon());
        deliveryRepository.perf = new CouponPerformance(8, 3, 2, new BigDecimal("120"));
        PerformanceView view = service.performance(COUPON);
        assertEquals(8, view.granted());
        assertEquals(3, view.used());
        assertEquals(new BigDecimal("37.5"), view.redemptionPct());
        assertEquals(2, view.ordersCount());
        assertEquals(new BigDecimal("120.00"), view.revenue());
        assertEquals(new BigDecimal("60.00"), view.avgOrderValue());
    }

    @Test
    void performance_nothingGranted_redemptionPctNullNotZero() {
        putCoupon(coupon());
        deliveryRepository.perf = new CouponPerformance(0, 0, 0, BigDecimal.ZERO);
        PerformanceView view = service.performance(COUPON);
        assertEquals(0, view.granted());
        assertNull(view.redemptionPct());
        assertNull(view.avgOrderValue());
        assertEquals(new BigDecimal("0.00"), view.revenue());
    }

    @Test
    void performance_usedButNoOrders_avgOrderValueNull() {
        putCoupon(coupon());
        deliveryRepository.perf = new CouponPerformance(4, 1, 0, BigDecimal.ZERO);
        PerformanceView view = service.performance(COUPON);
        assertEquals(new BigDecimal("25.0"), view.redemptionPct());
        assertNull(view.avgOrderValue());
    }

    @Test
    void performance_unknownCoupon_typed770() {
        CouponDeliveryException e = assertThrows(CouponDeliveryException.class,
                () -> service.performance(999));
        assertEquals(CouponDeliveryServiceImpl.ERRNO_COUPON_NOT_FOUND, e.getErrno());
    }
}
