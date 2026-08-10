package org.linlinjava.litemall.promotion.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.promotion.application.internal.LitemallCouponServiceImpl.CheckoutCouponReason;
import org.linlinjava.litemall.promotion.application.internal.LitemallCouponServiceImpl.CheckoutCouponView;
import org.linlinjava.litemall.promotion.application.ports.CouponMarginBasisPort;
import org.linlinjava.litemall.promotion.application.ports.CouponMarginBasisPort.MarginBasis;
import org.linlinjava.litemall.promotion.application.ports.CouponScopePort;
import org.linlinjava.litemall.promotion.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallUserCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallCouponRepository;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallUserCouponRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponDiscountType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponGoodsType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponStatus;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponTimeType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallUserCouponStatus;
import org.linlinjava.litemall.promotion.domain.service.LitemallCouponDomainService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave-24.1 read models against in-memory fakes: the goods-scoped receivable
 * list (PDP coupon strip — ALL always matches, goods scope by containment,
 * category scope through the ancestor chain) and the verbose checkout
 * classification (typed reasons expired &gt; exhausted &gt; scope &gt;
 * threshold, with the exact {@code minGap}).
 */
class CouponWave241ServiceTest {

    // ---- fakes (Wave-18 test shape) ----------------------------------

    private static class FakeCouponRepository implements LitemallCouponRepository {
        final Map<Integer, LitemallCouponAggregate> rows = new HashMap<>();
        final AtomicInteger sequence = new AtomicInteger(100);

        @Override
        public Optional<LitemallCouponAggregate> findById(LitemallCouponId couponId) {
            return Optional.ofNullable(rows.get(couponId.getId()));
        }

        @Override
        public List<LitemallCouponAggregate> findReceivable() {
            return rows.values().stream()
                    .filter(c -> LitemallCouponStatus.NORMAL.equals(c.getStatus()))
                    .toList();
        }

        @Override
        public Optional<LitemallCouponAggregate> findByCode(String code) {
            return rows.values().stream()
                    .filter(c -> code.equals(c.getCode())).findFirst();
        }

        @Override
        public List<LitemallCouponAggregate> findAll(int page, int limit) {
            return new ArrayList<>(rows.values());
        }

        @Override
        public void save(LitemallCouponAggregate coupon) {
            if (coupon.getCouponId() == null) {
                coupon.setCouponId(new LitemallCouponId(sequence.incrementAndGet()));
            }
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

    /**
     * Static scope facts mirroring the dev catalog shape: goods 10008302 sits
     * in leaf 1301 whose L1 root is 1300; goods 20001 sits in leaf 2101 under
     * L1 2100.
     */
    private static final CouponScopePort SCOPE = (goodsIds, categoryIds) -> {
        Set<Integer> leaves = new LinkedHashSet<>();
        if (categoryIds != null && !categoryIds.isEmpty()) {
            leaves.addAll(categoryIds);
        } else if (goodsIds != null) {
            for (Integer goodsId : goodsIds) {
                if (Integer.valueOf(10008302).equals(goodsId)) {
                    leaves.add(1301);
                }
                if (Integer.valueOf(20001).equals(goodsId)) {
                    leaves.add(2101);
                }
            }
        }
        Set<Integer> expanded = new LinkedHashSet<>(leaves);
        if (leaves.contains(1301)) {
            expanded.add(1300);
        }
        if (leaves.contains(2101)) {
            expanded.add(2100);
        }
        return new ArrayList<>(expanded);
    };

    private static final CouponMarginBasisPort HEALTHY_BASIS = (goodsIds, categoryIds) ->
            new MarginBasis(1300, 1180, 120, new BigDecimal("0.8"), new BigDecimal("4.99"));

    private FakeCouponRepository couponRepository;
    private FakeUserCouponRepository userCouponRepository;
    private LitemallCouponServiceImpl service;

    @BeforeEach
    void setUp() {
        couponRepository = new FakeCouponRepository();
        userCouponRepository = new FakeUserCouponRepository();
        LitemallDomainEventPublisher publisher = event -> { };
        service = new LitemallCouponServiceImpl(
                couponRepository, userCouponRepository,
                new LitemallCouponDomainService(), publisher,
                new CouponMarginGuard(HEALTHY_BASIS, new BigDecimal("1.05")),
                SCOPE);
    }

    private LitemallCouponAggregate saveCoupon(LitemallCouponGoodsType goodsType, Integer[] goodsValue,
                                               LitemallCouponStatus status, BigDecimal min) {
        LitemallCouponAggregate coupon = LitemallCouponAggregate.builder()
                .name("Coupon " + goodsType)
                .discount(new LitemallMoney(new BigDecimal("5.00")))
                .discountType(LitemallCouponDiscountType.FLAT)
                .min(new LitemallMoney(min))
                .type(LitemallCouponType.COMMON)
                .status(status)
                .goodsType(goodsType)
                .goodsValue(goodsValue)
                .timeType(LitemallCouponTimeType.DAYS)
                .build();
        couponRepository.save(coupon);
        return coupon;
    }

    private LitemallUserCouponAggregate hold(LitemallUserId userId, LitemallCouponAggregate coupon,
                                             LocalDateTime endTime) {
        LitemallUserCouponAggregate held = LitemallUserCouponAggregate.builder()
                .userId(userId)
                .couponId(coupon.getCouponId())
                .status(LitemallUserCouponStatus.USABLE)
                .endTime(endTime)
                .build();
        userCouponRepository.add(held);
        return held;
    }

    // ---- goods-scoped receivable list (PDP strip) ---------------------

    @Test
    void wholeCatalogCouponMatchesAnyGoods() {
        LitemallCouponAggregate all = saveCoupon(LitemallCouponGoodsType.ALL, null,
                LitemallCouponStatus.NORMAL, new BigDecimal("10.00"));
        List<LitemallCouponAggregate> matched = service.getReceivableCouponsForGoods(20001);
        assertEquals(1, matched.size());
        assertEquals(all.getCouponId(), matched.get(0).getCouponId());
    }

    @Test
    void goodsScopedCouponOnlyOnItsGoods() {
        saveCoupon(LitemallCouponGoodsType.ARRAY, new Integer[]{10008302},
                LitemallCouponStatus.NORMAL, new BigDecimal("10.00"));
        assertEquals(1, service.getReceivableCouponsForGoods(10008302).size());
        assertEquals(0, service.getReceivableCouponsForGoods(20001).size());
    }

    @Test
    void l1CategoryCouponMatchesLeafGoods_viaAncestorChain() {
        // Coupon scoped to L1 root 1300; the goods' own category is leaf 1301.
        saveCoupon(LitemallCouponGoodsType.CATEGORY, new Integer[]{1300},
                LitemallCouponStatus.NORMAL, new BigDecimal("10.00"));
        assertEquals(1, service.getReceivableCouponsForGoods(10008302).size());
        assertEquals(0, service.getReceivableCouponsForGoods(20001).size());
    }

    @Test
    void unscopedListStillReturnsEverythingReceivable() {
        saveCoupon(LitemallCouponGoodsType.ARRAY, new Integer[]{10008302},
                LitemallCouponStatus.NORMAL, new BigDecimal("10.00"));
        saveCoupon(LitemallCouponGoodsType.CATEGORY, new Integer[]{2100},
                LitemallCouponStatus.NORMAL, new BigDecimal("10.00"));
        assertEquals(2, service.getReceivableCoupons().size());
    }

    // ---- verbose checkout classification ------------------------------

    private static CheckoutCouponView only(List<CheckoutCouponView> views) {
        assertEquals(1, views.size());
        return views.get(0);
    }

    @Test
    void usableViewCarriesEffectiveDiscount() {
        LitemallUserId user = new LitemallUserId(1);
        hold(user, saveCoupon(LitemallCouponGoodsType.ALL, null,
                LitemallCouponStatus.NORMAL, new BigDecimal("10.00")), null);
        CheckoutCouponView view = only(service.getCheckoutCouponViews(
                user, new BigDecimal("60.00"), List.of(10008302), null));
        assertTrue(view.isUsable());
        assertNull(view.getReason());
        assertEquals(new BigDecimal("5.00"), view.getEffectiveDiscount());
    }

    @Test
    void thresholdMissReportsExactMinGap() {
        LitemallUserId user = new LitemallUserId(2);
        hold(user, saveCoupon(LitemallCouponGoodsType.ALL, null,
                LitemallCouponStatus.NORMAL, new BigDecimal("50.00")), null);
        CheckoutCouponView view = only(service.getCheckoutCouponViews(
                user, new BigDecimal("29.50"), List.of(10008302), null));
        assertEquals(CheckoutCouponReason.THRESHOLD, view.getReason());
        assertEquals(new BigDecimal("20.50"), view.getMinGap());
        assertNull(view.getEffectiveDiscount());
    }

    @Test
    void scopeMismatchReported_forGoodsAndCategoryScopes() {
        LitemallUserId user = new LitemallUserId(3);
        hold(user, saveCoupon(LitemallCouponGoodsType.ARRAY, new Integer[]{10008302},
                LitemallCouponStatus.NORMAL, new BigDecimal("10.00")), null);
        hold(user, saveCoupon(LitemallCouponGoodsType.CATEGORY, new Integer[]{1300},
                LitemallCouponStatus.NORMAL, new BigDecimal("10.00")), null);
        // Cart holds goods 20001 (leaf 2101 / L1 2100) — neither scope covers it.
        List<CheckoutCouponView> views = service.getCheckoutCouponViews(
                user, new BigDecimal("60.00"), List.of(20001), null);
        assertEquals(2, views.size());
        assertTrue(views.stream().allMatch(v -> CheckoutCouponReason.SCOPE.equals(v.getReason())));
    }

    @Test
    void expiredHoldingReported() {
        LitemallUserId user = new LitemallUserId(4);
        hold(user, saveCoupon(LitemallCouponGoodsType.ALL, null,
                        LitemallCouponStatus.NORMAL, new BigDecimal("10.00")),
                LocalDateTime.now().minusDays(1));
        CheckoutCouponView view = only(service.getCheckoutCouponViews(
                user, new BigDecimal("60.00"), List.of(10008302), null));
        assertEquals(CheckoutCouponReason.EXPIRED, view.getReason());
    }

    @Test
    void expiredCouponDefinitionReported() {
        LitemallUserId user = new LitemallUserId(5);
        hold(user, saveCoupon(LitemallCouponGoodsType.ALL, null,
                LitemallCouponStatus.EXPIRED, new BigDecimal("10.00")), null);
        CheckoutCouponView view = only(service.getCheckoutCouponViews(
                user, new BigDecimal("60.00"), List.of(10008302), null));
        assertEquals(CheckoutCouponReason.EXPIRED, view.getReason());
    }

    @Test
    void exhaustedCouponReported() {
        LitemallUserId user = new LitemallUserId(6);
        hold(user, saveCoupon(LitemallCouponGoodsType.ALL, null,
                LitemallCouponStatus.OUT, new BigDecimal("10.00")), null);
        CheckoutCouponView view = only(service.getCheckoutCouponViews(
                user, new BigDecimal("60.00"), List.of(10008302), null));
        assertEquals(CheckoutCouponReason.EXHAUSTED, view.getReason());
    }

    @Test
    void reasonPrecedence_expiredBeatsThreshold_scopeBeatsThreshold() {
        LitemallUserId user = new LitemallUserId(7);
        // Fails threshold AND is expired ⇒ expired wins.
        hold(user, saveCoupon(LitemallCouponGoodsType.ALL, null,
                LitemallCouponStatus.EXPIRED, new BigDecimal("500.00")), null);
        // Fails threshold AND scope ⇒ scope wins (spending more can't fix it).
        hold(user, saveCoupon(LitemallCouponGoodsType.ARRAY, new Integer[]{10008302},
                LitemallCouponStatus.NORMAL, new BigDecimal("500.00")), null);
        List<CheckoutCouponView> views = service.getCheckoutCouponViews(
                user, new BigDecimal("60.00"), List.of(20001), null);
        assertEquals(2, views.size());
        assertEquals(1, views.stream()
                .filter(v -> CheckoutCouponReason.EXPIRED.equals(v.getReason())).count());
        assertEquals(1, views.stream()
                .filter(v -> CheckoutCouponReason.SCOPE.equals(v.getReason())).count());
    }

    @Test
    void verboseAndBarePathAgreeOnUsableSet() {
        LitemallUserId user = new LitemallUserId(8);
        hold(user, saveCoupon(LitemallCouponGoodsType.CATEGORY, new Integer[]{1300},
                LitemallCouponStatus.NORMAL, new BigDecimal("10.00")), null);
        hold(user, saveCoupon(LitemallCouponGoodsType.ALL, null,
                LitemallCouponStatus.NORMAL, new BigDecimal("500.00")), null);

        List<LitemallCouponServiceImpl.UsableCouponView> bare = service.getUsableForCheckout(
                user, new BigDecimal("60.00"), List.of(10008302), null);
        List<CheckoutCouponView> verbose = service.getCheckoutCouponViews(
                user, new BigDecimal("60.00"), List.of(10008302), null);

        assertEquals(1, bare.size());
        assertEquals(bare.get(0).getCoupon().getCouponId(),
                verbose.stream().filter(CheckoutCouponView::isUsable)
                        .findFirst().orElseThrow().getCoupon().getCouponId());
        assertEquals(1, verbose.stream().filter(v -> !v.isUsable()).count());
    }
}
