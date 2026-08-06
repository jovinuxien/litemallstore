package org.linlinjava.litemall.promotion.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.promotion.application.internal.LitemallCouponServiceImpl.UsableCouponView;
import org.linlinjava.litemall.promotion.application.ports.CouponMarginBasisPort;
import org.linlinjava.litemall.promotion.application.ports.CouponMarginBasisPort.MarginBasis;
import org.linlinjava.litemall.promotion.application.ports.CouponScopePort;
import org.linlinjava.litemall.promotion.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallUserCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallIssueCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallRedeemCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallUpdateCouponCommand;
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
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave-18 service semantics against in-memory fakes: hard guard on
 * create/update (nothing saved on rejection), ancestor-aware scope matching
 * in the checkout selectlist (L1 coupon vs leaf cart, derived-category
 * parity), percent effective discount at redeem, redeem-time scope re-check,
 * and register-gift idempotency.
 */
class CouponWave18ServiceTest {

    // ---- fakes -------------------------------------------------------

    private static class FakeCouponRepository implements LitemallCouponRepository {
        final Map<Integer, LitemallCouponAggregate> rows = new HashMap<>();
        final AtomicInteger sequence = new AtomicInteger(100);
        int saves = 0;

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
            return rows.values().stream()
                    .filter(c -> code.equals(c.getCode())).findFirst();
        }

        @Override
        public List<LitemallCouponAggregate> findAll(int page, int limit) {
            return new ArrayList<>(rows.values());
        }

        @Override
        public void save(LitemallCouponAggregate coupon) {
            saves++;
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
     * in leaf 1301 whose L1 root is 1300.
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
            }
        }
        Set<Integer> expanded = new LinkedHashSet<>(leaves);
        if (leaves.contains(1301)) {
            expanded.add(1300);
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

    private LitemallIssueCouponCommand.LitemallIssueCouponCommandBuilder issue() {
        return LitemallIssueCouponCommand.builder()
                .name("Test coupon")
                .min(new BigDecimal("50.00"))
                .timeType(LitemallCouponTimeType.DAYS.getCode())
                .days(30);
    }

    // ---- guard on create/update --------------------------------------

    @Test
    void overGenerousCouponRejected_nothingSaved() {
        LitemallPromotionOperationResult result = service.issueCoupon(
                issue().discount(new BigDecimal("10.00")).build());
        assertFalse(result.isSuccess());
        assertEquals(CouponMarginGuard.CODE_DISCOUNT_EXCEEDS_MAX, result.getData().get("guardError"));
        assertTrue(result.getMessage().contains("8.00"), result.getMessage());
        assertEquals(0, couponRepository.saves);
    }

    @Test
    void guardedCouponSaved_withUncostedWarning() {
        LitemallPromotionOperationResult result = service.issueCoupon(
                issue().discount(new BigDecimal("8.00")).build());
        assertTrue(result.isSuccess());
        assertEquals(120, result.getData().get("uncostedCount"));
        assertEquals(1, couponRepository.saves);
    }

    @Test
    void percentCouponPersistsTypeAndCap() {
        LitemallPromotionOperationResult result = service.issueCoupon(
                issue().discount(new BigDecimal("10"))
                        .discountType(1)
                        .discountCap(new BigDecimal("15.00"))
                        .build());
        assertTrue(result.isSuccess());
        LitemallCouponAggregate saved = couponRepository.rows.values().iterator().next();
        assertEquals(LitemallCouponDiscountType.PERCENT, saved.getDiscountType());
        assertEquals(new BigDecimal("15.00"), saved.getDiscountCap().getAmount());
    }

    @Test
    void updateRerunsGuardOnMergedState() {
        service.issueCoupon(issue().discount(new BigDecimal("5.00")).build());
        LitemallCouponId id = new LitemallCouponId(couponRepository.rows.keySet().iterator().next());
        int savesAfterIssue = couponRepository.saves;

        LitemallUpdateCouponCommand raise = new LitemallUpdateCouponCommand();
        raise.setDiscount(new BigDecimal("20.00"));
        LitemallPromotionOperationResult rejected = service.updateCoupon(id, raise);
        assertFalse(rejected.isSuccess());
        assertEquals(CouponMarginGuard.CODE_DISCOUNT_EXCEEDS_MAX, rejected.getData().get("guardError"));
        assertEquals(savesAfterIssue, couponRepository.saves);

        LitemallUpdateCouponCommand ok = new LitemallUpdateCouponCommand();
        ok.setDiscount(new BigDecimal("7.00"));
        assertTrue(service.updateCoupon(id, ok).isSuccess());
    }

    // ---- ancestor-aware scope matching --------------------------------

    private LitemallCouponAggregate heldL1Coupon(LitemallUserId userId) {
        LitemallCouponAggregate coupon = LitemallCouponAggregate.builder()
                .name("L1 scoped")
                .discount(new LitemallMoney(new BigDecimal("5.00")))
                .discountType(LitemallCouponDiscountType.FLAT)
                .min(new LitemallMoney(new BigDecimal("10.00")))
                .type(LitemallCouponType.COMMON)
                .status(LitemallCouponStatus.NORMAL)
                .goodsType(LitemallCouponGoodsType.CATEGORY)
                .goodsValue(new Integer[]{1300})
                .timeType(LitemallCouponTimeType.DAYS)
                .build();
        couponRepository.save(coupon);
        LitemallUserCouponAggregate held = LitemallUserCouponAggregate.builder()
                .userId(userId)
                .couponId(coupon.getCouponId())
                .status(LitemallUserCouponStatus.USABLE)
                .build();
        userCouponRepository.add(held);
        return coupon;
    }

    @Test
    void l1ScopedCouponMatchesLeafCart_viaDerivedCategories() {
        LitemallUserId user = new LitemallUserId(7);
        heldL1Coupon(user);
        // Caller passes goods ids ONLY (the SPA selectlist path) — categories
        // are derived server-side and expanded to the L1 root.
        List<UsableCouponView> usable = service.getUsableForCheckout(
                user, new BigDecimal("60.00"), List.of(10008302), null);
        assertEquals(1, usable.size());
        assertEquals(new BigDecimal("5.00"), usable.get(0).getEffectiveDiscount());
    }

    @Test
    void l1ScopedCouponMatchesLeafCart_viaPassedLeafCategories() {
        LitemallUserId user = new LitemallUserId(8);
        heldL1Coupon(user);
        // Caller passes LEAF category ids (the order-submit path) — ancestor
        // expansion still reaches the L1-scoped coupon: parity by construction.
        List<UsableCouponView> usable = service.getUsableForCheckout(
                user, new BigDecimal("60.00"), List.of(10008302), List.of(1301));
        assertEquals(1, usable.size());
    }

    @Test
    void unrelatedCartDoesNotMatchScopedCoupon() {
        LitemallUserId user = new LitemallUserId(9);
        heldL1Coupon(user);
        List<UsableCouponView> usable = service.getUsableForCheckout(
                user, new BigDecimal("60.00"), List.of(555), null);
        assertTrue(usable.isEmpty());
    }

    @Test
    void percentCouponSelectlistCarriesComputedEffectiveDiscount() {
        LitemallUserId user = new LitemallUserId(10);
        LitemallCouponAggregate coupon = LitemallCouponAggregate.builder()
                .name("10% off")
                .discount(new LitemallMoney(new BigDecimal("10")))
                .discountType(LitemallCouponDiscountType.PERCENT)
                .discountCap(new LitemallMoney(new BigDecimal("7.00")))
                .min(new LitemallMoney(new BigDecimal("10.00")))
                .status(LitemallCouponStatus.NORMAL)
                .goodsType(LitemallCouponGoodsType.ALL)
                .timeType(LitemallCouponTimeType.DAYS)
                .build();
        couponRepository.save(coupon);
        userCouponRepository.add(LitemallUserCouponAggregate.builder()
                .userId(user).couponId(coupon.getCouponId())
                .status(LitemallUserCouponStatus.USABLE).build());

        List<UsableCouponView> usable = service.getUsableForCheckout(
                user, new BigDecimal("100.00"), List.of(10008302), null);
        assertEquals(1, usable.size());
        // 10% of 100 = 10.00, capped at 7.00
        assertEquals(new BigDecimal("7.00"), usable.get(0).getEffectiveDiscount());
    }

    // ---- redeem: scope re-check + percent math ------------------------

    @Test
    void redeemRechecksScopeWhenCartFactsPassed() {
        LitemallUserId user = new LitemallUserId(11);
        heldL1Coupon(user);
        Integer userCouponId = userCouponRepository.rows.keySet().iterator().next();

        LitemallPromotionOperationResult wrongCart = service.redeemCoupon(new LitemallRedeemCouponCommand(
                new LitemallUserCouponId(userCouponId), user, 900,
                new BigDecimal("60.00"), List.of(555), List.of(4200)));
        assertFalse(wrongCart.isSuccess());
        assertTrue(wrongCart.getMessage().contains("does not apply"), wrongCart.getMessage());

        LitemallPromotionOperationResult qualifying = service.redeemCoupon(new LitemallRedeemCouponCommand(
                new LitemallUserCouponId(userCouponId), user, 900,
                new BigDecimal("60.00"), List.of(10008302), null));
        assertTrue(qualifying.isSuccess());
        assertEquals(new BigDecimal("5.00"), qualifying.getData().get("discount"));
    }

    @Test
    void redeemWithoutCartFactsKeepsLegacyBehavior() {
        LitemallUserId user = new LitemallUserId(12);
        heldL1Coupon(user);
        Integer userCouponId = userCouponRepository.rows.keySet().iterator().next();
        LitemallPromotionOperationResult result = service.redeemCoupon(new LitemallRedeemCouponCommand(
                new LitemallUserCouponId(userCouponId), user, 901, new BigDecimal("60.00")));
        assertTrue(result.isSuccess());
    }

    @Test
    void redeemComputesPercentEffectiveDiscountAgainstSubtotal() {
        LitemallUserId user = new LitemallUserId(13);
        LitemallCouponAggregate coupon = LitemallCouponAggregate.builder()
                .name("10% off")
                .discount(new LitemallMoney(new BigDecimal("10")))
                .discountType(LitemallCouponDiscountType.PERCENT)
                .min(new LitemallMoney(new BigDecimal("10.00")))
                .status(LitemallCouponStatus.NORMAL)
                .goodsType(LitemallCouponGoodsType.ALL)
                .timeType(LitemallCouponTimeType.DAYS)
                .build();
        couponRepository.save(coupon);
        LitemallUserCouponAggregate held = LitemallUserCouponAggregate.builder()
                .userId(user).couponId(coupon.getCouponId())
                .status(LitemallUserCouponStatus.USABLE).build();
        userCouponRepository.add(held);

        LitemallPromotionOperationResult result = service.redeemCoupon(new LitemallRedeemCouponCommand(
                held.getUserCouponId(), user, 902, new BigDecimal("84.50")));
        assertTrue(result.isSuccess());
        assertEquals(new BigDecimal("8.45"), result.getData().get("discount"));
        assertEquals(1, result.getData().get("discountType"));
    }

    // ---- register gifts ----------------------------------------------

    @Test
    void registerGiftsGrantOnceAndAreIdempotent() {
        LitemallCouponAggregate gift = LitemallCouponAggregate.builder()
                .name("Welcome gift")
                .discount(new LitemallMoney(new BigDecimal("5.00")))
                .min(new LitemallMoney(new BigDecimal("40.00")))
                .type(LitemallCouponType.REGISTER)
                .status(LitemallCouponStatus.NORMAL)
                .goodsType(LitemallCouponGoodsType.ALL)
                .timeType(LitemallCouponTimeType.DAYS)
                .days(30)
                .build();
        couponRepository.save(gift);
        // A COMMON coupon must never be auto-granted.
        LitemallCouponAggregate common = LitemallCouponAggregate.builder()
                .name("Ordinary")
                .discount(new LitemallMoney(new BigDecimal("2.00")))
                .type(LitemallCouponType.COMMON)
                .status(LitemallCouponStatus.NORMAL)
                .goodsType(LitemallCouponGoodsType.ALL)
                .timeType(LitemallCouponTimeType.DAYS)
                .build();
        couponRepository.save(common);

        LitemallUserId user = new LitemallUserId(21);
        LitemallPromotionOperationResult first = service.grantRegisterGifts(user);
        assertTrue(first.isSuccess());
        assertEquals(1, first.getData().get("grantedCount"));
        assertEquals(List.of(gift.getCouponId().getId()), first.getData().get("granted"));
        assertEquals(1, userCouponRepository.countByUserAndCoupon(user, gift.getCouponId()));

        LitemallPromotionOperationResult second = service.grantRegisterGifts(user);
        assertTrue(second.isSuccess());
        assertEquals(0, second.getData().get("grantedCount"));
        assertEquals(1, userCouponRepository.countByUserAndCoupon(user, gift.getCouponId()));
    }
}
