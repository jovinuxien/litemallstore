package org.linlinjava.litemall.promotion.application.internal;

import org.linlinjava.litemall.promotion.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.promotion.domain.events.coupon.LitemallCouponIssuedEvent;
import org.linlinjava.litemall.promotion.domain.events.coupon.LitemallCouponReceivedEvent;
import org.linlinjava.litemall.promotion.domain.events.coupon.LitemallCouponRedeemedEvent;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallUserCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallIssueCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallReceiveCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallRedeemCouponCommand;
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
import java.util.Optional;

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
}
