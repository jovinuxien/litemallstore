package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallAddressRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallAddressId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallGoodsFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallPromotionFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.TaxCalculationPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.UsableCoupon;
import org.linlinjava.litemall.order.interfaces.dtos.cart.CheckoutSummaryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes the server-authoritative checkout total (Wave 7, Task D).
 *
 * <p><b>Deliberately delegates rather than re-implements.</b> Every number here comes from
 * the same collaborator {@code LitemallOrderServiceImpl.placeOrder} uses —
 * {@code FreightCalculationService} for freight, {@code LitemallPromotionFacade} for the
 * coupon, {@code TaxCalculationPort} for tax, and
 * {@code LitemallOrderServiceImpl.buildTaxableOrder} for the taxable base. A second
 * implementation of the money chain is exactly the bug this endpoint exists to kill (the
 * SPA has been reducing its own grand total from cart prices), so the only safe preview is
 * one that runs the same code.
 *
 * <p>Prices come from the cart rows, which are catalog-resolved at add time (Task E0), so
 * nothing here trusts a client figure.
 *
 * <p><b>Soft where it can be, hard where it must be.</b> A coupon that cannot be priced
 * shows as no discount rather than an error page — submit re-derives it and 503s there, so
 * the customer sees a total no LOWER than they will be charged. Tax is the opposite: a
 * missing destination legitimately means 0.00 (there is nothing to source yet), but an
 * enabled tax provider that FAILS propagates
 * {@code LitemallTaxUnavailableException} to the controller as a 503. Swallowing it would
 * render a total that omits tax — a number the customer is then charged past, which is the
 * exact preview/charge divergence this class exists to remove.
 */
@Service
public class CheckoutSummaryService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutSummaryService.class);

    @Autowired
    private LitemallCartServiceLayer cartServiceLayer;
    @Autowired
    private LitemallAddressRepository addressRepository;
    @Autowired
    private FreightCalculationService freightCalculationService;
    @Autowired
    private TaxCalculationPort taxCalculationPort;
    @Autowired
    private LitemallPromotionFacade promotionFacade;
    @Autowired
    private LitemallGoodsFacade goodsFacade;
    @Autowired
    private LitemallOrderServiceImpl orderServiceImpl;
    @Autowired
    private org.linlinjava.litemall.order.application.internal.cj.OrderSourceResolver orderSourceResolver;

    public CheckoutSummaryDto summarize(LitemallUserId userId, Integer addressId,
                                        Integer userCouponId, String countryCode) {
        List<LitemallCartAggregate> checked = cartServiceLayer.listAllCartItems(userId).stream()
                .filter(Objects::nonNull)
                .filter(LitemallCartAggregate::isChecked)
                .collect(Collectors.toList());

        BigDecimal goodsTotal = checked.stream()
                .map(item -> item.getPrice() == null
                        ? BigDecimal.ZERO
                        : item.getPrice().getAmount().multiply(BigDecimal.valueOf(item.getNumber())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        // findAddress is owner-scoped by contract: another user's addressId resolves to
        // null rather than to their address.
        LitemallAddressAggregate address = (addressId == null || addressId <= 0)
                ? null
                : addressRepository.findAddress(userId, new LitemallAddressId(addressId));

        BigDecimal couponPrice = resolveCoupon(userId, userCouponId, checked, goodsTotal);
        BigDecimal freight = resolveFreight(checked, goodsTotal, address, countryCode);
        BigDecimal tax = resolveTax(checked, goodsTotal, couponPrice, freight, countryCode, address);

        BigDecimal orderTotal = goodsTotal.add(freight).subtract(couponPrice).max(BigDecimal.ZERO).add(tax);

        CheckoutSummaryDto dto = new CheckoutSummaryDto();
        dto.setGoodsTotalPrice(goodsTotal);
        dto.setFreightPrice(freight);
        dto.setTaxPrice(tax);
        dto.setCouponPrice(couponPrice);
        dto.setOrderTotalPrice(orderTotal);
        // No points/integral deduction exists yet (integralPrice is a hardcoded zero on the
        // submit path too), so actual == total. Kept as its own field so the SPA reads the
        // charged amount from one place if that ever changes.
        dto.setActualPrice(orderTotal);
        dto.setCheckedGoodsList(checked.stream().map(CheckoutSummaryService::toLine).collect(Collectors.toList()));
        return dto;
    }

    /**
     * Tax needs a destination country, and the address book does not store one — submit
     * takes it from the place-order command, so the preview takes it from the caller too.
     * Without it (the SPA renders the cart before an address is chosen) report 0.00 rather
     * than blocking; submit still fails closed for the real destination.
     */
    private BigDecimal resolveTax(List<LitemallCartAggregate> checked, BigDecimal goodsTotal,
                                  BigDecimal couponPrice, BigDecimal freight,
                                  String countryCode, LitemallAddressAggregate address) {
        if (!taxCalculationPort.enabled() || checked.isEmpty()
                || countryCode == null || countryCode.isBlank()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return taxCalculationPort.quote(orderServiceImpl.buildTaxableOrder(
                checked, goodsTotal, couponPrice, freight, countryCode, address)).getAmount();
    }

    /**
     * Freight via the single authority, so preview and charge agree. Mirrors the submit
     * path's CJ handling; a preview has no pickup flag, so this quotes the delivery leg.
     */
    private BigDecimal resolveFreight(List<LitemallCartAggregate> checked, BigDecimal goodsTotal,
                                      LitemallAddressAggregate address, String countryCode) {
        if (checked.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        List<FreightCalculationService.FreightLine> lines = checked.stream()
                .map(item -> new FreightCalculationService.FreightLine(
                        item.getGoodsId().getId(),
                        item.getNumber() == null ? 0 : item.getNumber(),
                        item.getPrice() == null ? null : item.getPrice().getAmount()))
                .collect(Collectors.toList());
        return freightCalculationService.quote(lines, countryCode,
                address == null ? null : address.getProvince(), goodsTotal,
                LitemallOrderAggregate.SOURCE_CJ.equals(safeSource(checked))).getFreight();
    }

    /**
     * A mixed CJ+local cart is a submit-time error, not a preview-time one — the customer
     * should still see their cart priced. Fall back to local freight rather than throwing.
     */
    private String safeSource(List<LitemallCartAggregate> checked) {
        try {
            return orderSourceResolver.resolve(checked);
        } catch (RuntimeException e) {
            log.debug("Checkout preview could not resolve a single order source: {}", e.getMessage());
            return LitemallOrderAggregate.SOURCE_LOCAL;
        }
    }

    /**
     * Promotion stays the source of truth for coupons. Unlike submit — which 503s rather
     * than silently drop a discount the customer picked — a preview degrades to no discount
     * and logs it.
     */
    private BigDecimal resolveCoupon(LitemallUserId userId, Integer userCouponId,
                                     List<LitemallCartAggregate> checked, BigDecimal goodsTotal) {
        if (userCouponId == null || userCouponId <= 0 || checked.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        try {
            Optional<UsableCoupon> coupon = promotionFacade.findUsableCoupon(
                    userId, userCouponId, goodsTotal, goodsIds(checked), categoryIds(checked));
            return coupon.map(UsableCoupon::getDiscount).orElse(BigDecimal.ZERO.setScale(2));
        } catch (RuntimeException e) {
            log.warn("Checkout preview could not price coupon {} for user {}: {}",
                    userCouponId, userId.getId(), e.getMessage());
            return BigDecimal.ZERO.setScale(2);
        }
    }

    private Set<Integer> goodsIds(List<LitemallCartAggregate> checked) {
        return checked.stream().map(item -> item.getGoodsId().getId()).collect(Collectors.toSet());
    }

    /** Categories come from goods-management; promotion has no cart access by design. */
    private Set<Integer> categoryIds(List<LitemallCartAggregate> checked) {
        try {
            return goodsFacade.batchGetGoods(goodsIds(checked)).values().stream()
                    .filter(Objects::nonNull)
                    .map(goods -> goods.getCategoryId() == null ? null : goods.getCategoryId().getId())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (RuntimeException e) {
            log.warn("Checkout preview could not resolve cart categories: {}", e.getMessage());
            return Set.of();
        }
    }

    private static CheckoutSummaryDto.CheckoutLineDto toLine(LitemallCartAggregate item) {
        CheckoutSummaryDto.CheckoutLineDto line = new CheckoutSummaryDto.CheckoutLineDto();
        line.setCartId(item.getCartId() == null ? null : item.getCartId().getId());
        line.setGoodsId(item.getGoodsId().getId());
        line.setProductId(item.getProductId() == null ? null : item.getProductId().getId());
        line.setGoodsName(item.getGoodsName());
        line.setGoodsSn(item.getGoodsSn());
        line.setPicUrl(item.getPicUrl());
        line.setSpecifications(item.getSpecifications());
        line.setNumber(item.getNumber());
        line.setPrice(item.getPrice() == null ? null : item.getPrice().getAmount());
        return line;
    }
}
