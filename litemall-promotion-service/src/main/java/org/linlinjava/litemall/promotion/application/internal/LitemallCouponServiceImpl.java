package org.linlinjava.litemall.promotion.application.internal;

import org.linlinjava.litemall.promotion.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.promotion.domain.events.coupon.LitemallCouponIssuedEvent;
import org.linlinjava.litemall.promotion.domain.events.coupon.LitemallCouponReceivedEvent;
import org.linlinjava.litemall.promotion.domain.events.coupon.LitemallCouponRedeemedEvent;
import org.linlinjava.litemall.promotion.domain.events.coupon.LitemallCouponReleasedEvent;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallUserCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallExchangeCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallGrantCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallIssueCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallReceiveCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallRedeemCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallReleaseCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallUpdateCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallCouponRepository;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallUserCouponRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponGoodsType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponStatus;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponTimeType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallUserCouponStatus;
import org.linlinjava.litemall.promotion.domain.service.LitemallCouponDomainService;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Coupon vertical application service. Owns the receive→hold→redeem lifecycle
 * (crmeb StoreCoupon/UserCoupon) plus admin issuance, mirroring the bargain
 * vertical's orchestration style.
 */
@Service
@Transactional
public class LitemallCouponServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(LitemallCouponServiceImpl.class);

    private final LitemallCouponRepository couponRepository;
    private final LitemallUserCouponRepository userCouponRepository;
    private final LitemallCouponDomainService couponDomainService;
    private final LitemallDomainEventPublisher domainEventPublisher;

    public LitemallCouponServiceImpl(LitemallCouponRepository couponRepository,
                                     LitemallUserCouponRepository userCouponRepository,
                                     LitemallCouponDomainService couponDomainService,
                                     LitemallDomainEventPublisher domainEventPublisher) {
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.couponDomainService = couponDomainService;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * Admin: define and issue a new coupon. The definition is created NORMAL so
     * it is immediately receivable by customers.
     */
    public LitemallPromotionOperationResult issueCoupon(LitemallIssueCouponCommand command) {
        logger.info("Issuing coupon: name={}, type={}", command.getName(), command.getType());

        if (command.getDiscount() == null) {
            return LitemallPromotionOperationResult.couponIssueFailed("discount is required");
        }

        LitemallCouponAggregate coupon = LitemallCouponAggregate.builder()
                .name(command.getName())
                .description(command.getDescription())
                .tag(command.getTag())
                .total(command.getTotal())
                .discount(new LitemallMoney(command.getDiscount()))
                .min(command.getMin() != null ? new LitemallMoney(command.getMin()) : new LitemallMoney(BigDecimal.ZERO))
                .limitPerUser(command.getLimitPerUser())
                .type(command.getType() != null ? LitemallCouponType.fromCode(command.getType()) : LitemallCouponType.COMMON)
                .status(LitemallCouponStatus.NORMAL)
                .goodsType(command.getGoodsType() != null ? LitemallCouponGoodsType.fromCode(command.getGoodsType()) : LitemallCouponGoodsType.ALL)
                .goodsValue(command.getGoodsValue())
                .code(command.getCode())
                .timeType(command.getTimeType() != null ? LitemallCouponTimeType.fromCode(command.getTimeType()) : LitemallCouponTimeType.DAYS)
                .days(command.getDays())
                .startTime(command.getStartTime())
                .endTime(command.getEndTime())
                .build();

        couponRepository.save(coupon);

        domainEventPublisher.publish(new LitemallCouponIssuedEvent(coupon.getCouponId(), coupon.getName()));

        Map<String, Object> data = new HashMap<>();
        data.put("couponId", coupon.getCouponId().getId());
        data.put("name", coupon.getName());
        return LitemallPromotionOperationResult.couponIssued(data);
    }

    /**
     * Customer: claim a coupon into the user's wallet. Enforces availability,
     * receive window, redemption-code match, per-user limit and total cap, then
     * stamps the computed validity window on the held coupon.
     */
    public LitemallPromotionOperationResult receiveCoupon(LitemallReceiveCouponCommand command) {
        logger.info("Receiving coupon: couponId={}, userId={}",
                command.getCouponId().getId(), command.getUserId().getId());

        Optional<LitemallCouponAggregate> couponOpt = couponRepository.findById(command.getCouponId());
        if (couponOpt.isEmpty()) {
            return LitemallPromotionOperationResult.couponReceiveFailed("Coupon not found");
        }
        LitemallCouponAggregate coupon = couponOpt.get();

        LocalDateTime now = LocalDateTime.now();
        if (!coupon.isAvailable()) {
            return LitemallPromotionOperationResult.couponReceiveFailed("Coupon is not available");
        }
        if (!coupon.withinReceiveWindow(now)) {
            return LitemallPromotionOperationResult.couponReceiveFailed("Coupon is outside its receive window");
        }
        if (coupon.isCodeType() && (command.getCode() == null || !command.getCode().equals(coupon.getCode()))) {
            return LitemallPromotionOperationResult.couponReceiveFailed("Invalid redemption code");
        }
        if (!coupon.isUnlimitedPerUser()) {
            int held = userCouponRepository.countByUserAndCoupon(command.getUserId(), command.getCouponId());
            if (held >= coupon.getLimitPerUser()) {
                return LitemallPromotionOperationResult.couponReceiveFailed("Per-user receive limit reached");
            }
        }
        if (!coupon.isUnlimitedTotal()) {
            int issued = userCouponRepository.countByCoupon(command.getCouponId());
            if (issued >= coupon.getTotal()) {
                return LitemallPromotionOperationResult.couponReceiveFailed("Coupon is fully issued");
            }
        }

        LitemallCouponDomainService.ValidityWindow window =
                couponDomainService.computeValidityWindow(coupon, now);

        LitemallUserCouponAggregate held = LitemallUserCouponAggregate.builder()
                .userId(command.getUserId())
                .couponId(command.getCouponId())
                .status(LitemallUserCouponStatus.USABLE)
                .startTime(window.getStart())
                .endTime(window.getEnd())
                .build();
        userCouponRepository.add(held);

        domainEventPublisher.publish(new LitemallCouponReceivedEvent(
                held.getUserCouponId(), command.getUserId(), command.getCouponId()));

        Map<String, Object> data = new HashMap<>();
        data.put("userCouponId", held.getUserCouponId().getId());
        data.put("couponId", command.getCouponId().getId());
        data.put("startTime", window.getStart());
        data.put("endTime", window.getEnd());
        return LitemallPromotionOperationResult.couponReceived(data);
    }

    /**
     * Redeem-at-checkout: validate ownership, usability, expiry and spend
     * threshold, then mark the held coupon USED against the order. Order-side
     * invocation (from checkout) is wired by the order worktree.
     */
    public LitemallPromotionOperationResult redeemCoupon(LitemallRedeemCouponCommand command) {
        logger.info("Redeeming coupon: userCouponId={}, orderId={}",
                command.getUserCouponId().getId(), command.getOrderId());

        Optional<LitemallUserCouponAggregate> heldOpt =
                userCouponRepository.findById(command.getUserCouponId());
        if (heldOpt.isEmpty()) {
            return LitemallPromotionOperationResult.couponRedeemFailed("Held coupon not found");
        }
        LitemallUserCouponAggregate held = heldOpt.get();

        if (!held.isOwnedBy(command.getUserId())) {
            return LitemallPromotionOperationResult.couponRedeemFailed("Coupon does not belong to this user");
        }
        if (!held.isUsable()) {
            return LitemallPromotionOperationResult.couponRedeemFailed("Coupon is not usable");
        }

        LocalDateTime now = LocalDateTime.now();
        if (held.isExpired(now)) {
            held.setStatus(LitemallUserCouponStatus.EXPIRED);
            userCouponRepository.update(held);
            return LitemallPromotionOperationResult.couponRedeemFailed("Coupon has expired");
        }

        Optional<LitemallCouponAggregate> couponOpt = couponRepository.findById(held.getCouponId());
        if (couponOpt.isEmpty()) {
            return LitemallPromotionOperationResult.couponRedeemFailed("Coupon definition not found");
        }
        LitemallCouponAggregate coupon = couponOpt.get();

        BigDecimal subtotalValue = command.getOrderSubtotal() != null ? command.getOrderSubtotal() : BigDecimal.ZERO;
        if (!coupon.meetsThreshold(new LitemallMoney(subtotalValue))) {
            return LitemallPromotionOperationResult.couponRedeemFailed(
                    "Order subtotal does not meet the coupon threshold");
        }

        held.redeem(command.getOrderId(), now);
        userCouponRepository.update(held);

        domainEventPublisher.publish(new LitemallCouponRedeemedEvent(
                held.getUserCouponId(), command.getUserId(), held.getCouponId(),
                command.getOrderId(), coupon.getDiscount()));

        Map<String, Object> data = new HashMap<>();
        data.put("userCouponId", held.getUserCouponId().getId());
        data.put("couponId", held.getCouponId().getId());
        data.put("orderId", command.getOrderId());
        data.put("discount", coupon.getDiscount() != null ? coupon.getDiscount().getAmount() : null);
        return LitemallPromotionOperationResult.couponRedeemed(data);
    }

    /**
     * Exchange-by-code: resolve the coupon from its redemption code, then run
     * the full receive path (window, caps, code match) so exchange can never
     * bypass a claim rule.
     */
    public LitemallPromotionOperationResult exchangeCoupon(LitemallExchangeCouponCommand command) {
        logger.info("Exchanging coupon code for userId={}", command.getUserId().getId());

        if (command.getCode() == null || command.getCode().isBlank()) {
            return LitemallPromotionOperationResult.couponExchangeFailed("Redemption code is required");
        }
        Optional<LitemallCouponAggregate> couponOpt = couponRepository.findByCode(command.getCode().trim());
        if (couponOpt.isEmpty()) {
            return LitemallPromotionOperationResult.couponExchangeFailed("Unknown redemption code");
        }
        LitemallCouponAggregate coupon = couponOpt.get();
        if (!coupon.isCodeType()) {
            return LitemallPromotionOperationResult.couponExchangeFailed("Coupon is not a code-exchange coupon");
        }

        LitemallPromotionOperationResult received = receiveCoupon(new LitemallReceiveCouponCommand(
                command.getUserId(), coupon.getCouponId(), command.getCode().trim()));
        return received.isSuccess()
                ? LitemallPromotionOperationResult.couponExchanged(received.getData())
                : LitemallPromotionOperationResult.couponExchangeFailed(received.getMessage());
    }

    /**
     * Release a redemption after its order failed (spec: coupon-checkout
     * contract). Idempotent: a coupon already back in USABLE reports success so
     * order-side retries are safe.
     */
    public LitemallPromotionOperationResult releaseCoupon(LitemallReleaseCouponCommand command) {
        logger.info("Releasing coupon: userCouponId={}, orderId={}",
                command.getUserCouponId().getId(), command.getOrderId());

        Optional<LitemallUserCouponAggregate> heldOpt =
                userCouponRepository.findById(command.getUserCouponId());
        if (heldOpt.isEmpty()) {
            return LitemallPromotionOperationResult.couponReleaseFailed("Held coupon not found");
        }
        LitemallUserCouponAggregate held = heldOpt.get();

        if (!held.isOwnedBy(command.getUserId())) {
            return LitemallPromotionOperationResult.couponReleaseFailed("Coupon does not belong to this user");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("userCouponId", held.getUserCouponId().getId());
        data.put("couponId", held.getCouponId().getId());

        if (held.isUsable()) {
            data.put("alreadyReleased", true);
            return LitemallPromotionOperationResult.couponReleased(data);
        }

        try {
            held.release(command.getOrderId());
        } catch (IllegalStateException e) {
            return LitemallPromotionOperationResult.couponReleaseFailed(e.getMessage());
        }
        userCouponRepository.update(held);

        domainEventPublisher.publish(new LitemallCouponReleasedEvent(
                held.getUserCouponId(), command.getUserId(), held.getCouponId(), command.getOrderId()));

        return LitemallPromotionOperationResult.couponReleased(data);
    }

    /** Admin: update a coupon definition; null command fields stay unchanged. */
    public LitemallPromotionOperationResult updateCoupon(LitemallCouponId couponId,
                                                         LitemallUpdateCouponCommand command) {
        Optional<LitemallCouponAggregate> couponOpt = couponRepository.findById(couponId);
        if (couponOpt.isEmpty()) {
            return LitemallPromotionOperationResult.couponUpdateFailed("Coupon not found");
        }
        LitemallCouponAggregate coupon = couponOpt.get();

        if (command.getName() != null) coupon.setName(command.getName());
        if (command.getDescription() != null) coupon.setDescription(command.getDescription());
        if (command.getTag() != null) coupon.setTag(command.getTag());
        if (command.getTotal() != null) coupon.setTotal(command.getTotal());
        if (command.getDiscount() != null) coupon.setDiscount(new LitemallMoney(command.getDiscount()));
        if (command.getMin() != null) coupon.setMin(new LitemallMoney(command.getMin()));
        if (command.getLimitPerUser() != null) coupon.setLimitPerUser(command.getLimitPerUser());
        if (command.getType() != null) coupon.setType(LitemallCouponType.fromCode(command.getType()));
        if (command.getStatus() != null) coupon.setStatus(LitemallCouponStatus.fromCode(command.getStatus()));
        if (command.getGoodsType() != null) coupon.setGoodsType(LitemallCouponGoodsType.fromCode(command.getGoodsType()));
        if (command.getGoodsValue() != null) coupon.setGoodsValue(command.getGoodsValue());
        if (command.getCode() != null) coupon.setCode(command.getCode());
        if (command.getTimeType() != null) coupon.setTimeType(LitemallCouponTimeType.fromCode(command.getTimeType()));
        if (command.getDays() != null) coupon.setDays(command.getDays());
        if (command.getStartTime() != null) coupon.setStartTime(command.getStartTime());
        if (command.getEndTime() != null) coupon.setEndTime(command.getEndTime());

        couponRepository.save(coupon);

        Map<String, Object> data = new HashMap<>();
        data.put("couponId", coupon.getCouponId().getId());
        return LitemallPromotionOperationResult.couponUpdated(data);
    }

    /** Admin: logical delete; already-held user coupons stay redeemable. */
    public LitemallPromotionOperationResult deleteCoupon(LitemallCouponId couponId) {
        Optional<LitemallCouponAggregate> couponOpt = couponRepository.findById(couponId);
        if (couponOpt.isEmpty()) {
            return LitemallPromotionOperationResult.couponDeleteFailed("Coupon not found");
        }
        couponRepository.delete(couponId);

        Map<String, Object> data = new HashMap<>();
        data.put("couponId", couponId.getId());
        return LitemallPromotionOperationResult.couponDeleted(data);
    }

    /**
     * Admin: push a coupon straight into a user's wallet (crmeb direct-send).
     * Skips the receive window and redemption code — an admin grant is an
     * explicit decision — but still refuses a withdrawn or fully-issued coupon.
     */
    public LitemallPromotionOperationResult grantCoupon(LitemallGrantCouponCommand command) {
        logger.info("Granting coupon {} to userId={}",
                command.getCouponId().getId(), command.getUserId().getId());

        Optional<LitemallCouponAggregate> couponOpt = couponRepository.findById(command.getCouponId());
        if (couponOpt.isEmpty()) {
            return LitemallPromotionOperationResult.couponGrantFailed("Coupon not found");
        }
        LitemallCouponAggregate coupon = couponOpt.get();
        if (!coupon.isAvailable()) {
            return LitemallPromotionOperationResult.couponGrantFailed("Coupon is not available");
        }
        if (!coupon.isUnlimitedTotal()) {
            int issued = userCouponRepository.countByCoupon(command.getCouponId());
            if (issued >= coupon.getTotal()) {
                return LitemallPromotionOperationResult.couponGrantFailed("Coupon is fully issued");
            }
        }

        LitemallCouponDomainService.ValidityWindow window =
                couponDomainService.computeValidityWindow(coupon, LocalDateTime.now());

        LitemallUserCouponAggregate held = LitemallUserCouponAggregate.builder()
                .userId(command.getUserId())
                .couponId(command.getCouponId())
                .status(LitemallUserCouponStatus.USABLE)
                .startTime(window.getStart())
                .endTime(window.getEnd())
                .build();
        userCouponRepository.add(held);

        domainEventPublisher.publish(new LitemallCouponReceivedEvent(
                held.getUserCouponId(), command.getUserId(), command.getCouponId()));

        Map<String, Object> data = new HashMap<>();
        data.put("userCouponId", held.getUserCouponId().getId());
        data.put("couponId", command.getCouponId().getId());
        return LitemallPromotionOperationResult.couponGranted(data);
    }

    // =========================================================================
    // READ MODELS
    // =========================================================================

    @Transactional(readOnly = true)
    public List<LitemallCouponAggregate> getReceivableCoupons() {
        return couponRepository.findReceivable();
    }

    @Transactional(readOnly = true)
    public Optional<LitemallCouponAggregate> getCoupon(LitemallCouponId couponId) {
        return couponRepository.findById(couponId);
    }

    @Transactional(readOnly = true)
    public List<LitemallCouponAggregate> listCoupons(int page, int limit) {
        return couponRepository.findAll(page, limit);
    }

    @Transactional(readOnly = true)
    public List<LitemallUserCouponAggregate> getMyUsableCoupons(LitemallUserId userId) {
        return userCouponRepository.findUsableByUser(userId);
    }

    /**
     * My coupons filtered by status bucket. Expiry is reconciled lazily: a
     * USABLE holding past its end time is persisted as EXPIRED on read, so the
     * buckets the SPA renders (0 unused / 1 used / 2 expired) are always true.
     */
    public List<LitemallUserCouponAggregate> getMyCoupons(LitemallUserId userId,
                                                          LitemallUserCouponStatus status) {
        LocalDateTime now = LocalDateTime.now();
        List<LitemallUserCouponAggregate> all = userCouponRepository.findByUser(userId, null);
        for (LitemallUserCouponAggregate held : all) {
            if (held.isUsable() && held.isExpired(now)) {
                held.setStatus(LitemallUserCouponStatus.EXPIRED);
                userCouponRepository.update(held);
            }
        }
        if (status == null) {
            return all;
        }
        return all.stream()
                .filter(h -> status.equals(h.getStatus()))
                .collect(Collectors.toList());
    }

    /** A held coupon joined with its definition, for checkout selection views. */
    public static class UsableCouponView {
        private final LitemallUserCouponAggregate userCoupon;
        private final LitemallCouponAggregate coupon;

        public UsableCouponView(LitemallUserCouponAggregate userCoupon, LitemallCouponAggregate coupon) {
            this.userCoupon = userCoupon;
            this.coupon = coupon;
        }

        public LitemallUserCouponAggregate getUserCoupon() { return userCoupon; }
        public LitemallCouponAggregate getCoupon() { return coupon; }
    }

    /**
     * Usable-for-this-checkout query (spec: coupon-checkout contract): of my
     * unexpired holdings, which apply to a cart of {@code amount} covering
     * {@code goodsIds}/{@code categoryIds}. The caller supplies the cart facts —
     * promotion has no cart access by design.
     */
    @Transactional(readOnly = true)
    public List<UsableCouponView> getUsableForCheckout(LitemallUserId userId, BigDecimal amount,
                                                       List<Integer> goodsIds, List<Integer> categoryIds) {
        LocalDateTime now = LocalDateTime.now();
        LitemallMoney subtotal = new LitemallMoney(amount != null ? amount : BigDecimal.ZERO);
        return userCouponRepository.findUsableByUser(userId).stream()
                .filter(held -> !held.isExpired(now))
                .map(held -> couponRepository.findById(held.getCouponId())
                        .map(coupon -> new UsableCouponView(held, coupon))
                        .orElse(null))
                .filter(Objects::nonNull)
                .filter(view -> view.getCoupon().isAvailable())
                .filter(view -> view.getCoupon().meetsThreshold(subtotal))
                .filter(view -> view.getCoupon().matchesGoods(goodsIds, categoryIds))
                .collect(Collectors.toList());
    }

    /** Admin: who received a coupon (paged issuance records). */
    @Transactional(readOnly = true)
    public List<LitemallUserCouponAggregate> listIssueRecords(LitemallCouponId couponId,
                                                              int page, int limit) {
        return userCouponRepository.findByCoupon(couponId, page, limit);
    }
}
