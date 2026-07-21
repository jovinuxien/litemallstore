package org.linlinjava.litemall.order.application.internal;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import com.google.protobuf.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.core.notify.NotifyService;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.core.task.TaskService;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.order.application.LitemallIOrderService;
import org.linlinjava.litemall.order.application.util.exception.coupon.LitemallInvalidCouponException;
import org.linlinjava.litemall.order.domain.model.agregates.*;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderSubmitResult;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.service.groupon.LitemallGrouponValidationResult;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderDomainService;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.model.repositories.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallCategoryId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.AggregatesValidationContext;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallGoodsFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallPromotionFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.CouponRedemption;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.UsableCoupon;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.UserServiceFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.TaxCalculationPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.tax.TaxQuote;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.tax.TaxableOrder;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


@Service
@Slf4j
public class LitemallOrderServiceImpl implements LitemallIOrderService {

    private final LitemallOrderRepository orderRepository;
    private final LitemallCartRepository cartRepository;
    private final LitemallAddressRepository addressRepository;
    private final LitemallOrderGoodsRepository orderGoodsRepository;


    @Autowired
    private final LitemallDomainEventPublisher domainEventPublisher;
    // Service internal to orderService
    @Autowired
    private final LitemallCouponServiceLayer couponService;
    @Autowired
    private LitemallOrderDomainService orderDomainService;
    @Autowired
    private LitemallCartServiceLayer cartServiceLayer;
    @Autowired
    private LitemallGrouponServiceLayer grouponServiceLayer;

    @Autowired
    private  NotifyService notifyService;
    @Autowired
    private  TaskService taskService;
    @Autowired
    private LitemallGoodsFacade goodsFacade;
    @Autowired
    private UserServiceFeignClient userServiceFeignClient;
    // Append-only status-history store. Written in the SAME transaction as each status
    // change so the customer/admin timeline never loses a hop.
    @Autowired
    private LitemallOrderStatusHistoryRepository statusHistoryRepository;
    // Tags the order 'local' | 'cj' from its cart lines (rejects a mixed cart) so the
    // pay step knows whether to replay the order to CJ createOrder.
    @Autowired
    private org.linlinjava.litemall.order.application.internal.cj.OrderSourceResolver orderSourceResolver;
    // Submit-time gate: a CJ order every line of which can be fulfilled at CJ (has a
    // cj_vid) before it is placed, so an unfulfillable line fails at submit (422) instead
    // of at payment. Only consulted for CJ-sourced orders.
    @Autowired
    private org.linlinjava.litemall.order.application.internal.cj.CjOrderAvailabilityChecker cjOrderAvailabilityChecker;
    // CJ-side cleanup on cancel: delete the CJ order while CJ still allows it (Wave 3).
    // Defensive on the cancel paths — under pay-first a CREATED local order normally has
    // no CJ order yet; the real window is a placed-but-unpaid CJ draft.
    @Autowired
    private org.linlinjava.litemall.order.application.internal.cj.CjFulfillmentService cjFulfillmentService;
    // ACL to the promotion service's coupon contract (validate/redeem/release).
    // Promotion owns the coupon tables now — the placement path must never read them
    // directly. See docs/adr-promotion-coupon-facade.md.
    @Autowired
    private LitemallPromotionFacade promotionFacade;

    // Single freight authority (Wave 4): the same service backs the quote endpoint, so
    // what checkout previews is exactly what submit charges. Injected (not constructed)
    // so its template cache is shared with the admin CRUD invalidation path.
    @Autowired
    private FreightCalculationService freightCalculationService;

    // Tax seam (Wave 7): the same port backs GET /srv/cart/checkout, for exactly the
    // reason freight is shared above. FAILS CLOSED — never catch its exception into a
    // zero; disabled (default) yields 0.00 via ZeroTaxAdapter.
    @Autowired
    private TaxCalculationPort taxCalculationPort;

    // Pickup stores (Wave 4, Task B): store existence/visibility checks at submit.
    @Autowired
    private LitemallStoreServiceLayer storeServiceLayer;

    /** 10-digit pickup verify codes (Wave 4) — SecureRandom, dup-key retried at assign. */
    private final java.security.SecureRandom verifyCodeRandom = new java.security.SecureRandom();

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public LitemallOrderServiceImpl(LitemallOrderRepository orderRepo,
                                    LitemallGrouponRepository grouponRepo,
                                    LitemallCartRepository cartRepo,
                                    LitemallAddressRepository addressRepo,
                                    LitemallOrderGoodsRepository orderGoodsRepo,
                                    LitemallCouponServiceLayer couponService,
                                    LitemallDomainEventPublisher domainEventPublisher) {
        this.orderRepository = orderRepo;
        this.cartRepository = cartRepo;
        this.addressRepository = addressRepo;
        this.orderGoodsRepository = orderGoodsRepo;
        this.couponService = couponService;
        this.domainEventPublisher = domainEventPublisher;
    }


    @Override
    //public LitemallOrderSubmitResult placeOrder(LitemallPlaceOrderCommand command)  {
    @Transactional
    public LitemallOrderSubmitResult placeOrder(LitemallPlaceOrderCommand command) throws ServiceException {

        // Validate the command
        if(command.getUserId() == null){
            throw new IllegalArgumentException("User id is required.");
        }

        // addressId is OPTIONAL: when the command carries none (the customer SPA has no
        // address picker yet — /srv/address follow-up), fall back to the user's default
        // shipping address below. Resolution + the "no address at all" guard happen where
        // the address is actually loaded.

        // Groupon and coupon are OPTIONAL checkout selections. litemall encodes
        // "none" as the sentinel 0 (couponId may also be -1 = none); cartId 0 means
        // "whole checked cart". A plain order arrives with these null/0, so normalize
        // null -> 0 here rather than rejecting it. The previous hard null-checks 500'd
        // every order placed without a groupon AND a coupon.
        int cartId = command.getCartId() == null ? 0 : command.getCartId();
        int grouponLinkId = command.getGrouponLinkId() == null ? 0 : command.getGrouponLinkId();

        LitemallUserId cmdUserId = new LitemallUserId(command.getUserId());
        LitemallGrouponRulesId cmdGrouponRulesId = new LitemallGrouponRulesId(
                command.getGrouponRulesId() == null ? 0 : command.getGrouponRulesId());
        LitemallCouponId cmdCouponId = new LitemallCouponId(
                command.getCouponId() == null ? 0 : command.getCouponId());
        // -1 is a "none" sentinel like couponId's; the VO rejects negatives, so clamp
        // it to 0 here or a couponless submit carrying -1 dies as a 500.
        LitemallCouponUserId cmdCouponUserId = new LitemallCouponUserId(
                command.getUserCouponId() == null || command.getUserCouponId() < 0
                        ? 0 : command.getUserCouponId());


       /* LitemallUserAggregate user = FeignResponseHandler.handleResponse(userServiceFeignClient.geUserById(userId.getId()), "Get userAggregate by Id");

        if(user == null){
            throw new IllegalArgumentException("User is not found");
        }*/

        // Validate the groupon rules ONLY for an actual groupon purchase. A plain
        // order carries grouponRulesId 0, which must skip validation — otherwise
        // validateGrouponRules throws "Groupon rules not found" for the non-existent
        // rule 0 and fails every normal checkout. Args follow the method signature
        // order (grouponRulesId, grouponLinkId, userId); the previous call passed
        // them as (userId, rulesId, linkId), mis-validating every groupon order.
        boolean grouponValid = cmdGrouponRulesId.getId() > 0
                && grouponServiceLayer.validateGrouponRules(
                        cmdGrouponRulesId.getId(), grouponLinkId, cmdUserId.getId()).isValid();

        // Delivery mode (Wave 4, Task B): express (default) needs a shipping address;
        // in-store pickup needs a visible store + pickup contact instead. The orchestrator
        // pre-checks all pickup 422s OUTSIDE this transaction; the checks here are the
        // safety-net copies for other callers/races and throw the TYPED
        // LitemallPickupException, which the orchestrator rethrows (never converts
        // in-transaction — the rollback-only→502 landmine) and REST maps to 422.
        boolean pickup = command.isPickup();
        LitemallAddressAggregate addressAggregate = null;
        org.linlinjava.litemall.db.domain.LitemallStore pickupStore = null;
        if (pickup) {
            pickupStore = storeServiceLayer.findPickupable(command.getStoreId());
            if (pickupStore == null) {
                throw new org.linlinjava.litemall.order.application.util.exception.order.LitemallPickupException(
                        "The selected pickup store is not available.");
            }
            if (isBlank(command.getPickupName()) || isBlank(command.getPickupMobile())) {
                throw new org.linlinjava.litemall.order.application.util.exception.order.LitemallPickupException(
                        "Pickup contact name and mobile are required.");
            }
        } else {
            // Get and Check the shipping address. Use the explicit addressId when supplied,
            // otherwise fall back to the user's default address. Either way a missing address
            // is a hard error — the order needs a consignee/mobile/address to ship to.
            addressAggregate = command.getAddressId() == null
                    ? addressRepository.findDefaultAddress(cmdUserId)
                    : addressRepository.findAddress(cmdUserId, new LitemallAddressId(command.getAddressId()));
            if (addressAggregate == null) {
                throw new IllegalArgumentException("Address info is required");
            }
        }


        // Get the Checked cart items
        List<LitemallCartAggregate> cartList = null;
        cartList = cartServiceLayer.getCheckedCartItems(new LitemallCartId(cartId), cmdUserId);

        // An order needs at least one checked, non-deleted cart line. Without this
        // guard an empty cart still creates a zero-line order and only fails much
        // later in validateAndReduceStock with the misleading "Failed to load
        // required product data for validation" (empty goodsIds -> empty maps).
        // Fail fast with an accurate, client-facing message instead — handleOrderCreation
        // maps LitemallOrderServiceException to a clean submitFailed result (not a 500).
        if (cartList == null || cartList.stream().allMatch(java.util.Objects::isNull) || cartList.isEmpty()) {
            throw new org.linlinjava.litemall.order.application.util.exception.order.LitemallOrderServiceException(
                    "Cannot place an order: there are no checked items in the cart for user " + cmdUserId.getId());
        }

        // Fulfillment source for the whole order ('local' | 'cj'), from the goods rows.
        // Throws on a mixed cart — the orchestrator pre-checks this outside the
        // transaction so a mixed submit surfaces as a clean 422, not a rollback-only 500.
        String orderSource = orderSourceResolver.resolve(cartList);

        // CJ lines cannot be picked up in a store — CJ ships from its own warehouses.
        // Pre-checked in the orchestrator; typed safety-net here (see above).
        if (pickup && LitemallOrderAggregate.SOURCE_CJ.equals(orderSource)) {
            throw new org.linlinjava.litemall.order.application.util.exception.order.LitemallPickupException(
                    "Dropshipped items cannot be picked up in store — choose delivery or remove them.");
        }

        // For a CJ order, prove every line can be placed at CJ (has a cj_vid) BEFORE
        // creating the order. A shallow catalog-fill row marked on-sale before enrichment
        // carries no cj_vid; without this gate it would only fail at pay time (rolling the
        // payment back). Fail cleanly at submit (422) instead. Local orders skip this.
        if (LitemallOrderAggregate.SOURCE_CJ.equals(orderSource)) {
            cjOrderAvailabilityChecker.assertAllFulfillable(cartList);
        }

        // Validate the productStock through the goods ACL (price/stock authoritative read)
        this.orderDomainService.validateProductStock(cartList, goodsFacade);

        // Group purchase discount
        BigDecimal grouponPrice = new BigDecimal(0);  // initialize grouponPrice is not redundant;
        if(grouponValid) {
            grouponPrice = grouponServiceLayer.getGrouponDiscount(cmdGrouponRulesId).getAmount();
        }

        // Calculate checked goods price
        BigDecimal checkedGoodsPrice;  // initialize checkedGoodsPrice is redundant;
        LitemallGrouponRulesAggregate grouponRulesAggregate = grouponServiceLayer.getGrouponRulesAggregate(cmdGrouponRulesId);
        checkedGoodsPrice = this.orderDomainService.priceCalculation(cartList, grouponRulesAggregate, new LitemallMoney(grouponPrice));


        // Coupon application. Promotion is the source of truth: ask it which of the
        // user's held coupons are usable for THIS checkout (subtotal + the cart's
        // goods/category ids — promotion has no cart access by design) and require
        // the selected one to be among them. Anything else — not owned, expired,
        // below threshold, out of goods scope — is a clean 422 with no order row and
        // the coupon untouched. Promotion unreachable with a coupon selected is a
        // 503: never silently drop a discount the customer picked.
        BigDecimal couponPrice = new BigDecimal(0);
        UsableCoupon appliedCoupon = null;
        boolean couponSelected = (cmdCouponId.getId() != 0 && cmdCouponId.getId() != -1)
                || cmdCouponUserId.getId() > 0;
        if (couponSelected) {
            if (cmdCouponUserId.getId() <= 0) {
                throw new LitemallInvalidCouponException(
                        "a coupon was selected but no userCouponId was supplied");
            }
            Set<Integer> cartGoodsIds = cartList.stream()
                    .map(item -> item.getGoodsId().getId())
                    .collect(Collectors.toSet());
            Set<Integer> cartCategoryIds = goodsFacade.batchGetGoods(cartGoodsIds).values().stream()
                    .map(LitemallGoodsAggregate::getCategoryId)
                    .filter(Objects::nonNull)
                    .map(LitemallCategoryId::getId)
                    .collect(Collectors.toSet());
            appliedCoupon = promotionFacade.findUsableCoupon(
                            cmdUserId, cmdCouponUserId.getId(), checkedGoodsPrice,
                            cartGoodsIds, cartCategoryIds)
                    .orElseThrow(() -> new LitemallInvalidCouponException(
                            "the selected coupon is not usable for this checkout "
                            + "(not owned, expired, below its minimum spend, or out of scope)"));
            couponPrice = appliedCoupon.getDiscount() == null
                    ? new BigDecimal(0) : appliedCoupon.getDiscount();
        }

        // Freight (Wave 4, Task A): ONE authority — FreightCalculationService — prices
        // freight for both this submit path and POST /srv/order/freight-quote, so the
        // quoted and charged amounts can never disagree. The service internally applies
        // the template ladder when freight.template.enabled=true and degrades to the
        // legacy litemall_express_freight_min/value flat rule otherwise (and for CJ
        // carts, which skip templates by design). See docs/adr-freight-templates.md.
        // In-store pickup has no shipping leg at all — freight is 0 by definition.
        BigDecimal freightPrice;
        if (pickup) {
            freightPrice = BigDecimal.ZERO;
        } else {
            List<FreightCalculationService.FreightLine> freightLines = cartList.stream()
                    .filter(Objects::nonNull)
                    .map(item -> new FreightCalculationService.FreightLine(
                            item.getGoodsId().getId(), item.getNumber() == null ? 0 : item.getNumber(),
                            item.getPrice() == null ? null : item.getPrice().getAmount()))
                    .collect(Collectors.toList());
            freightPrice = freightCalculationService.quote(freightLines,
                    command.getCountryCode(), addressAggregate.getProvince(), checkedGoodsPrice,
                    LitemallOrderAggregate.SOURCE_CJ.equals(orderSource)).getFreight();
        }
        // Other money available，For example, user points
        BigDecimal integralPrice = new BigDecimal(0);

        // Tax (Wave 7, Task C): the SAME port GET /srv/cart/checkout previews with, so the
        // quoted total and the charged total are the same number by construction — the
        // freight lesson applied to tax. FAILS CLOSED: when tax is enabled and cannot be
        // computed, LitemallTaxUnavailableException propagates out of this transaction and
        // the REST layer answers 503. Never caught into a zero — an untaxed order is a
        // silent, permanent liability, unlike a retryable checkout error.
        // Disabled (the default) ⇒ ZeroTaxAdapter ⇒ 0.00 and no behaviour change.
        TaxQuote taxQuote = taxCalculationPort.quote(buildTaxableOrder(
                cartList, checkedGoodsPrice, couponPrice, freightPrice,
                command.getCountryCode(), addressAggregate));
        BigDecimal taxPrice = taxQuote.getAmount();

        // Order Fee calculation: goods − coupon + freight + tax. Tax is computed on the
        // POST-discount base (see buildTaxableOrder) and added last, so it is never itself
        // discounted and never charged on money the customer didn't pay.
        BigDecimal orderTotalPrice = checkedGoodsPrice.add(freightPrice).subtract(couponPrice)
                .max(new BigDecimal(0)).add(taxPrice);

        // Final payment: removing integralPrice from orderTotalPrice
        BigDecimal actualPrice = orderTotalPrice.subtract(integralPrice);

        LitemallOrderId newOrderId = null;
        LitemallOrderAggregate orderAggregate =  new LitemallOrderAggregate();

        // Order creation
        newOrderId = new LitemallOrderId(0);// the OrderId to be generated
        orderAggregate.setOrderId(newOrderId);
        orderAggregate.setUserId(cmdUserId);// authoritative buyer from the gateway header
        orderAggregate.setOrderSn(orderRepository.generateOrderSn(cmdUserId));

        orderAggregate.setOrderStatus(LitemallOrderStatus.CREATED);
        // Creation-time defaults for fields the data mapper dereferences but that a
        // fresh order doesn't carry yet (else convertToDataModel NPEs / inserts null
        // into NOT NULL columns).
        orderAggregate.setAfterSaleStatus(org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus.STATUS_INIT);
        orderAggregate.setRefundAmount(new LitemallMoney(new BigDecimal(0)));
        orderAggregate.setRefundType("");
        orderAggregate.setComments((short) 0);
        orderAggregate.setDeleted(false);
        orderAggregate.setAddTime(LocalDateTime.now());
        orderAggregate.setUpdateTime(LocalDateTime.now());
        orderAggregate.setMessage(command.getMessage());
        if (pickup) {
            // Pickup (Wave 4): consignee/mobile are the pickup contact; the 127-char
            // address column stores ONLY the marker + store name (store detail renders
            // from litemall_store via store_id). No shipping address is captured.
            orderAggregate.setConsignee(command.getPickupName());
            orderAggregate.setMobile(command.getPickupMobile());
            orderAggregate.setAddress("PICKUP: " + pickupStore.getName());
            orderAggregate.setDeliveryType(LitemallOrderAggregate.DELIVERY_PICKUP);
            orderAggregate.setStoreId(pickupStore.getId());
        } else {
            orderAggregate.setConsignee(addressAggregate.getName());
            orderAggregate.setMobile(addressAggregate.getTel());
            String detailedAddress = addressAggregate.getProvince() + addressAggregate.getCity() + addressAggregate.getCounty() + " " + addressAggregate.getAddressDetail();
            orderAggregate.setAddress(detailedAddress);
            // CJ-fulfillment linkage (V27): keep the structured-address key + checkout
            // country so the pay step can replay a source='cj' order to CJ createOrder.
            orderAggregate.setAddressId(addressAggregate.getAddressId());
            orderAggregate.setDeliveryType(LitemallOrderAggregate.DELIVERY_EXPRESS);
        }
        orderAggregate.setCountryCode(command.getCountryCode());
        orderAggregate.setSource(orderSource);
        orderAggregate.setGoodsPrice(new LitemallMoney(checkedGoodsPrice));
        orderAggregate.setFreightPrice(new LitemallMoney(freightPrice));
        orderAggregate.setCouponPrice(new LitemallMoney(couponPrice));
        orderAggregate.setIntegralPrice(new LitemallMoney(integralPrice));
        orderAggregate.setTaxPrice(new LitemallMoney(taxPrice));
        // Provider breakdown for invoices/audit; null when nothing was collected.
        orderAggregate.setTaxBreakdown(taxQuote.getBreakdownJson());
        orderAggregate.setOrderPrice(new LitemallMoney(orderTotalPrice));
        orderAggregate.setActualPrice(new LitemallMoney(actualPrice));

        if(grouponRulesAggregate != null){
            orderAggregate.setGrouponPrice(new LitemallMoney(grouponPrice));
        }else {
            orderAggregate.setGrouponPrice(new LitemallMoney(new BigDecimal(0)));
        }
        // Save the order
        orderRepository.addOrder(orderAggregate);

        // Get the id from the order
        LitemallOrderAggregate existingOrderAggregate = orderRepository.findById(orderAggregate.getOrderId()).orElseThrow(() -> new NoSuchElementException("Order not found"));

        // First hop of the lifecycle timeline: the order was placed (→ CREATED). Recorded
        // in THIS transaction so a row exists from the very first state. fromStatus is null.
        statusHistoryRepository.record(new LitemallOrderStatusChange(
                existingOrderAggregate.getOrderId(), null, LitemallOrderStatus.CREATED,
                "create", "Order placed", "user", LocalDateTime.now()));

        // Add the order goods items information
        for(LitemallCartAggregate cartGoods: cartList){

            LitemallOrderGoodsAggregate orderGoodsAggregate = new LitemallOrderGoodsAggregate();
            orderGoodsAggregate.setOrderId(existingOrderAggregate.getOrderId());
            orderGoodsAggregate.setGoodsId(cartGoods.getGoodsId());
            orderGoodsAggregate.setProductId(cartGoods.getProductId());

            orderGoodsAggregate.setGoodsSn(cartGoods.getGoodsSn());
            orderGoodsAggregate.setGoodsName(cartGoods.getGoodsName());

            orderGoodsAggregate.setPrice(cartGoods.getPrice());
            orderGoodsAggregate.setNumber(cartGoods.getNumber().shortValue());
            orderGoodsAggregate.setSpecifications(cartGoods.getSpecifications());
            orderGoodsAggregate.setPicUrl(cartGoods.getPicUrl());

            orderGoodsAggregate.setAddTime(LocalDateTime.now());
            orderGoodsAggregate.setUpdateTime(LocalDateTime.now());

            orderGoodsRepository.add(orderGoodsAggregate);
        }
        // Clear the cart
        clearCart(cmdUserId, new LitemallCartId(cartId));

        // Redeem the coupon EXACTLY-ONCE at promotion, now that the order row exists
        // (redeem stamps the consuming order id). A business 400 aborts the placement:
        // this transaction rolls back, no order survives, and the coupon was never
        // consumed. After a successful redeem, any later failure in this transaction
        // (groupon insert, stock reservation) must give the coupon back — registered
        // as a rollback-time compensation, mirroring the stock-restore pattern.
        if (appliedCoupon != null) {
            LitemallOrderId redeemOrderId = existingOrderAggregate.getOrderId();
            CouponRedemption redemption = promotionFacade.redeemCoupon(
                    cmdUserId, appliedCoupon.getUserCouponId(), redeemOrderId, checkedGoodsPrice);
            registerCouponReleaseOnRollback(cmdUserId, appliedCoupon.getUserCouponId(), redeemOrderId);
            // The order was priced with the validate-step discount; if promotion's
            // authoritative redeem-time discount disagrees, refuse to place a
            // mispriced order (the rollback releases the just-redeemed coupon).
            if (redemption.getDiscount() != null && redemption.getDiscount().compareTo(couponPrice) != 0) {
                throw new LitemallInvalidCouponException(
                        "the coupon discount changed between validation and redemption — please retry");
            }
        }

        // If it's a groupon purchase project, add group buying information
        Integer createdGrouponLinkId = grouponServiceLayer.createGrouponOrder(
                grouponLinkId, cmdUserId.getId(), cmdGrouponRulesId.getId(), existingOrderAggregate.getOrderId());

        if (createdGrouponLinkId != null) {
            // Handle groupon-specific logic if needed
            log.info("Groupon order created with link ID: {}", createdGrouponLinkId);
        }

        // Reserve/reduce stock LAST — the remote decrement (a Feign call to
        // goods-management) cannot be rolled back by this local DB transaction, so
        // it is kept as the final mutation to minimise the window in which a later
        // step could fail after stock is taken. reduceStockForAllItems additionally
        // registers a rollback-time compensating restore (best-effort).
        validateAndReduceStock(cartList);

        //Validate and process groupon if available
        return new LitemallOrderSubmitResult(
                existingOrderAggregate.getOrderId().getId(),
                existingOrderAggregate.getOrderSn(),
                true, // the order is CREATED/unpaid — pay is a separate action (/actions/pay)
                grouponLinkId,
                existingOrderAggregate.getActualPrice().getAmount(),
                LocalDateTime.now(),
                LitemallOrderSubmitResult.LitemallOrderSubmitResultStatus.SUCCESS
        );
    }

    /**
     *
     * @param userId
     * @param cartId
     */
    private void clearCart(LitemallUserId userId, LitemallCartId cartId){
        if(cartId.getId() == 0){
            cartRepository.clearCheckedByUserId(userId);
        }else {
            cartRepository.deleteById(cartId);
        }
    }
   /* private LitemallGrouponAggregate handleGrouponCreation(LitemallPlaceOrderCommand command, LitemallOrderAggregateRoot orderAggregateRoot, LitemallGrouponRulesAggregate rulesAggregate) {

        if (rulesAggregate == null) {
            return null;
        }
        LitemallGrouponAggregate grouponAggregate;
        LitemallGrouponAggregate baseGroupon = null;
        if (command.getGrouponRulesId() != null && command.getGrouponLinkId() > 0) {
            baseGroupon = grouponRepository.findById(new LitemallGrouponId(command.getGrouponLinkId()));
            grouponAggregate = LitemallGrouponAggregate.createJoin(orderAggregateRoot.getOrderAggregate().getOrderId(), orderAggregateRoot.getOrderAggregate().getUserId(), rulesAggregate.getGrouponRulesId(), baseGroupon);

        } else {
            grouponAggregate = LitemallGrouponAggregate.createNewGroupon(orderAggregateRoot.getOrderAggregate().getOrderId(), orderAggregateRoot.getOrderAggregate().getUserId(), rulesAggregate.getGrouponRulesId());
        }

        grouponRepository.saveGroupon(grouponAggregate);
        return grouponAggregate;

    }*/

    public void publishDomainEvents(LitemallGrouponAggregate grouponAggregate){
        grouponAggregate.getDomainEvents().forEach(this.domainEventPublisher::publish);

    }

    public Optional<LitemallOrderAggregate> getOrderAggregate(LitemallOrderId orderId) {
        return orderRepository.findById(orderId);
    }

    /**
     * Drain the aggregate's recorded status transitions into the history table
     * (within the caller's transaction) and clear them so they are not double-written.
     */
    private void persistStatusHistory(LitemallOrderAggregate agg) {
        for (LitemallOrderStatusChange change : agg.getStatusChanges()) {
            statusHistoryRepository.record(change);
        }
        agg.getStatusChanges().clear();
    }

    /**
     * Publish the aggregate's pending domain events, then clear them. Done after the
     * status write + history persist so subscribers only see committed transitions.
     */
    private void publishAndClearEvents(LitemallOrderAggregate agg) {
        agg.getDomainEvents().forEach(domainEventPublisher::publish);
        agg.getDomainEvents().clear();
    }

    /**
     * Customer-initiated cancellation (CREATED → CANCELED). Applies the guarded
     * status update, records the transition to the history timeline, releases
     * reserved stock (best-effort) and publishes the resulting domain events — all
     * in one transaction.
     */
    public void cancelOrder(LitemallOrderId orderId, String reason) {
        LitemallOrderAggregate orderAggregate = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        orderAggregate.cancel(reason); // validates CREATED→CANCELED, records change + event
        int updated = orderRepository.markCanceledIfCreated(orderId);
        if (updated == 0) {
            throw new IllegalStateException(
                    "Order " + orderId.getId() + " can no longer be cancelled (already paid/cancelled)");
        }
        restoreStockForOrder(orderId);
        cjFulfillmentService.cancelAtCjIfDeletable(orderAggregate, "customer cancel");
        releaseCouponOnCancelCommit(orderAggregate);
        persistStatusHistory(orderAggregate);
        publishAndClearEvents(orderAggregate);
    }

    /**
     * System-initiated cancellation (e.g. the unpaid-order sweep). Transitions the
     * order to SYSTEM_CANCELED; otherwise identical to {@link #cancelOrder}.
     *
     * <p>Runs in its OWN transaction (REQUIRES_NEW): the sweep calls this while
     * holding {@code FOR UPDATE SKIP LOCKED} claim-locks on the task rows, so an
     * independent transaction here means a failure rolls back only this order's
     * work and does not mark the sweep's claim transaction rollback-only. It
     * touches order tables only (never the task table), so there is no lock
     * contention with the sweep's claim.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoCancelOrder(LitemallOrderId orderId, String reason) {
        LitemallOrderAggregate orderAggregate = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        orderAggregate.autoCancel(); // validates CREATED→SYSTEM_CANCELED, records change + event
        int updated = orderRepository.markSystemCanceledIfCreated(orderId);
        if (updated == 0) {
            // The order left CREATED between the sweep's selection and now (e.g. the
            // customer paid). That is not an error for the sweep — just skip it.
            log.info("Skipping auto-cancel of order {}: no longer in CREATED state", orderId.getId());
            return;
        }
        restoreStockForOrder(orderId);
        cjFulfillmentService.cancelAtCjIfDeletable(orderAggregate, "auto cancel (unpaid timeout)");
        releaseCouponOnCancelCommit(orderAggregate);
        persistStatusHistory(orderAggregate);
        publishAndClearEvents(orderAggregate);
    }

    /**
     * Mark an order as paid: validate + apply the CREATED→PAID transition on the
     * aggregate, persist the status (+ pay_time + the tender in pay_id) and history,
     * and publish the resulting domain events. Runs inside the caller's transaction
     * so it is atomic with the payment debit.
     *
     * @param payId tender record for {@code pay_id}: {@code "WALLET"} or
     *              {@code "<METHOD>:<pspReference>"}; refund settlement routes by it.
     */
    public void markOrderPaid(LitemallOrderId orderId, String payId, String paymentIntentId) {
        LitemallOrderAggregate orderAggregate = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        orderAggregate.markAsPaid();
        orderAggregate.setPayId(payId);
        orderAggregate.setPaymentIntentId(paymentIntentId);

        // Conditional CREATED->PAID transition: the UPDATE only matches a row still
        // in CREATED, so a retried or concurrent PAY (which already flipped the row)
        // affects 0 rows. We then abort, rolling back any wallet debit applied in
        // this same transaction — preventing a double charge for one order.
        //
        // A PaymentIntent already bound to ANOTHER order trips the UNIQUE index here
        // instead, raising DuplicateKeyException — this transaction rolls back and the
        // REST layer answers 402 (Wave 7, Task A2).
        int updated = orderRepository.markPaidIfCreated(orderId, payId, paymentIntentId);
        if (updated == 0) {
            throw new IllegalStateException(
                    "Order " + orderId.getId() + " is no longer in CREATED state; payment already applied");
        }

        // Pickup verify code (Wave 4): generated INSIDE this payment transaction — an
        // unpaid order never carries a redeemable code (documented deviation from
        // crmeb's generate-at-create). The UNIQUE index dedupes; a collision with
        // another order's code raises a duplicate-key error and we regenerate.
        if (orderAggregate.isPickup()) {
            assignPickupVerifyCode(orderId);
        }

        persistStatusHistory(orderAggregate);
        publishAndClearEvents(orderAggregate);
    }

    /**
     * Build the tax provider's view of a checkout (Wave 7, Task C). Public so
     * {@code GET /srv/cart/checkout} computes tax from the identical inputs the submit path
     * uses — preview and charge must agree, and the only way to guarantee that is to share
     * this code rather than to write it twice.
     *
     * <p><b>The coupon is allocated across lines proportionally</b> rather than ignored.
     * Stripe Tax's Calculation API has no discount concept, so a discount has to reach it
     * as reduced line amounts; passing full prices would tax a sale larger than the one
     * that happened and over-collect from the customer. Any rounding remainder lands on the
     * first line, so the taxable base sums exactly to (goods − coupon) and cannot drift a
     * cent from what is actually charged.
     */
    public TaxableOrder buildTaxableOrder(List<LitemallCartAggregate> cartList,
                                          BigDecimal checkedGoodsPrice,
                                          BigDecimal couponPrice,
                                          BigDecimal freightPrice,
                                          String countryCode,
                                          LitemallAddressAggregate address) {
        BigDecimal discountedGoods = checkedGoodsPrice.subtract(couponPrice).max(BigDecimal.ZERO);
        boolean discounting = couponPrice.signum() > 0 && checkedGoodsPrice.signum() > 0;

        List<TaxableOrder.Line> lines = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        List<LitemallCartAggregate> items = cartList.stream().filter(Objects::nonNull).collect(Collectors.toList());

        for (int i = 0; i < items.size(); i++) {
            LitemallCartAggregate item = items.get(i);
            int quantity = item.getNumber() == null ? 0 : item.getNumber();
            BigDecimal unit = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice().getAmount();
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(quantity));

            BigDecimal taxableLine = lineTotal;
            if (discounting) {
                taxableLine = i == items.size() - 1
                        // Last line absorbs the remainder so the parts sum to the whole.
                        ? discountedGoods.subtract(allocated).max(BigDecimal.ZERO)
                        : lineTotal.multiply(discountedGoods)
                                .divide(checkedGoodsPrice, 2, java.math.RoundingMode.HALF_UP);
                allocated = allocated.add(taxableLine);
            }

            // Re-express the discounted line as a unit price so the provider sees a
            // consistent (unit x quantity) pair.
            BigDecimal taxableUnit = quantity == 0
                    ? BigDecimal.ZERO
                    : taxableLine.divide(BigDecimal.valueOf(quantity), 2, java.math.RoundingMode.HALF_UP);
            lines.add(new TaxableOrder.Line(item.getGoodsId().getId(), quantity, taxableUnit));
        }

        return new TaxableOrder(lines, freightPrice, countryCode,
                address == null ? null : address.getProvince(),
                address == null ? null : address.getPostalCode(),
                address == null ? null : address.getCity(),
                address == null ? null : address.getAddressDetail(),
                null);
    }

    /** Assign a fresh 10-digit verify code with a bounded dup-key retry (see markOrderPaid). */
    private void assignPickupVerifyCode(LitemallOrderId orderId) {
        for (int attempt = 1; attempt <= 5; attempt++) {
            String code = String.format("%010d", Math.floorMod(verifyCodeRandom.nextLong(), 10_000_000_000L));
            try {
                // 0 rows = the order already carries a code (idempotent under pay retries).
                orderRepository.assignVerifyCode(orderId, code);
                return;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                log.warn("verify code collision for order {} (attempt {}), regenerating",
                        orderId.getId(), attempt);
            }
        }
        // 5 collisions in a 10^10 space means something is broken — fail the payment
        // cleanly rather than produce a paid pickup order that can never be redeemed.
        throw new IllegalStateException(
                "Could not assign a unique pickup verify code for order " + orderId.getId());
    }

    /**
     * Staff writes off a paid pickup order at the counter (PAID → DELIVERED, Wave 4).
     * Mirrors {@link #confirmDelivery}: aggregate transition (raises the delivered
     * event + timeline hop) then the guarded conditional UPDATE — 0 rows = a
     * concurrent scan already redeemed the code (distinct 422 upstream).
     */
    public void writeoffOrder(LitemallOrderId orderId, String verifiedBy) {
        LitemallOrderAggregate agg = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        agg.writeOff(verifiedBy);
        int updated = orderRepository.markDeliveredByWriteoff(orderId, verifiedBy);
        if (updated == 0) {
            throw new org.linlinjava.litemall.order.application.util.exception.order.LitemallWriteoffException(
                    org.linlinjava.litemall.order.application.util.exception.order.LitemallWriteoffException.Kind.ALREADY_VERIFIED,
                    "This pickup code was already redeemed (concurrent scan)");
        }
        persistStatusHistory(agg);
        publishAndClearEvents(agg);
    }

    /**
     * Admin/fulfillment ships a paid order (PAID → SHIPPED). Guarded so only a paid
     * order can ship; records the transition and publishes the shipped event.
     */
    public void shipOrder(LitemallOrderId orderId, String shipChannel, String shipSn) {
        LitemallOrderAggregate agg = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        agg.ship(shipChannel, shipSn);
        int updated = orderRepository.markShippedIfPaid(orderId, shipChannel, shipSn, agg.getShipTime());
        if (updated == 0) {
            throw new IllegalStateException("Order " + orderId.getId() + " cannot ship: not in PAID state");
        }
        persistStatusHistory(agg);
        publishAndClearEvents(agg);
    }

    /** Customer confirms receipt (SHIPPED → DELIVERED). */
    public void confirmDelivery(LitemallOrderId orderId) {
        LitemallOrderAggregate agg = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        agg.confirmDelivery();
        int updated = orderRepository.markDeliveredIfShipped(orderId, agg.getConfirmTime());
        if (updated == 0) {
            throw new IllegalStateException("Order " + orderId.getId() + " cannot be confirmed: not in SHIPPED state");
        }
        persistStatusHistory(agg);
        publishAndClearEvents(agg);
    }

    /**
     * System auto-confirms a shipped order after the grace window
     * (SHIPPED → AUTO_DELIVERED). Own transaction, like {@link #autoCancelOrder}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoConfirmOrder(LitemallOrderId orderId) {
        LitemallOrderAggregate agg = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        agg.autoConfirm();
        int updated = orderRepository.markAutoDeliveredIfShipped(orderId, agg.getConfirmTime());
        if (updated == 0) {
            log.info("Skipping auto-confirm of order {}: no longer in SHIPPED state", orderId.getId());
            return;
        }
        persistStatusHistory(agg);
        publishAndClearEvents(agg);
    }

    /** Customer opens a refund/return (PAID|SHIPPED → REFUND_REQUEST). */
    public void requestRefund(LitemallOrderId orderId, String reason) {
        LitemallOrderAggregate agg = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        agg.requestRefund(reason);
        int updated = orderRepository.markRefundRequestedIfPayable(orderId, reason);
        if (updated == 0) {
            throw new IllegalStateException(
                    "Order " + orderId.getId() + " cannot request refund: not in PAID/SHIPPED state");
        }
        persistStatusHistory(agg);
        publishAndClearEvents(agg);
    }

    /**
     * Admin approves a refund; the money has already been returned by the caller
     * (REFUND_REQUEST → REFUNDED). The orchestrator settles the money to the paying
     * tender first (wallet credit or PSP-reversal seam) inside this same transaction,
     * and {@code amount} is the settled, capture-capped figure — persisted as
     * {@code refund_amount} so the row records what was actually returned.
     */
    public void refundOrder(LitemallOrderId orderId, LitemallMoney amount) {
        LitemallOrderAggregate agg = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        agg.refund(amount);
        int updated = orderRepository.markRefundedIfRequested(
                orderId, amount == null ? null : amount.getAmount(), agg.getRefundTime());
        if (updated == 0) {
            throw new IllegalStateException(
                    "Order " + orderId.getId() + " cannot be refunded: not in REFUND_REQUEST state");
        }
        persistStatusHistory(agg);
        publishAndClearEvents(agg);
    }

    /** Soft-delete a terminal order (sets the logical-delete flag). */
    public void deleteOrder(LitemallOrderId orderId) {
        orderRepository.deleteByOrderId(orderId);
    }

    /** Full status-history timeline for an order, oldest first. */
    public List<LitemallOrderStatusChange> getStatusHistory(LitemallOrderId orderId) {
        return statusHistoryRepository.findByOrderId(orderId);
    }

    /**
     * Best-effort release of the stock reserved for an order's lines (used when an
     * order is cancelled). Reads the persisted order-goods rows and asks the goods
     * ACL to add the quantities back; never throws.
     */
    private void restoreStockForOrder(LitemallOrderId orderId) {
        List<LitemallOrderGoodsAggregate> orderGoods = orderGoodsRepository.findByOId(orderId);
        if (orderGoods == null || orderGoods.isEmpty()) {
            return;
        }
        Map<Integer, Integer> productQuantities = orderGoods.stream()
                .collect(Collectors.toMap(
                        g -> g.getProductId().getId(),
                        g -> (int) g.getNumber(),
                        Integer::sum));
        goodsFacade.restoreStock(productQuantities);
    }
    /**
     * @Desc: Validate and reduce stock for all items in a batch
     * @param cartList
     */
    private void validateAndReduceStock(List<LitemallCartAggregate> cartList) {
        // STEP 1: Load all required data in batch
        AggregatesValidationContext context = loadAggregatesValidationContext(cartList);

        if (!context.isValidForValidation()) {
            throw new org.linlinjava.litemall.order.application.util.exception.product.LitemallGoodsServiceUnavailableException(
                    "failed to load required product data for stock validation");
        }

        // STEP 2: Validate stock for all items
        validateStockForAllItems(cartList, context);

        // STEP 3: Reduce stock for all items in batch
        reduceStockForAllItems(cartList, context);
    }
    /**
     *
     * @param cartList
     * @return
     */
    private AggregatesValidationContext loadAggregatesValidationContext(List<LitemallCartAggregate> cartList){

        try {
            //Extract all required IDs from one pass
            Set<Integer> goodsIds = cartList.stream()
                    .map(item -> item.getGoodsId().getId())
                    .collect(Collectors.toSet());

            Set<Integer> productIds = cartList.stream()
                    .map(item -> item.getProductId().getId())
                    .collect(Collectors.toSet());

            // Batch load all data in parallel
            CompletableFuture<Map<LitemallGoodsId, LitemallGoodsAggregate>> goodsFuture =
                    CompletableFuture.supplyAsync(() ->
                           batchGetGoodsAggregates(goodsIds));

            CompletableFuture<Map<LitemallGoodsProductId, LitemallGoodsProductAggregate>> productsFuture =
                    CompletableFuture.supplyAsync(() ->
                            batchGetProductAggregates(goodsIds));

            // Wait for all batch requests to complete
            //Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap = goodsFuture.get();
            Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap = goodsFuture.join();
            Map<LitemallGoodsProductId, LitemallGoodsProductAggregate> productsMap = productsFuture.join();

            return AggregatesValidationContext.create(goodsMap, productsMap);


        } catch (Exception e) {
            // A goods-management outage must fail placement cleanly (rollback, no
            // stock taken), not be swallowed into an empty validation context.
            Throwable cause = (e instanceof java.util.concurrent.CompletionException && e.getCause() != null)
                    ? e.getCause() : e;
            log.error("Failed to load goods from the goods ACL for cartList {}", cartList, cause);
            if (cause instanceof org.linlinjava.litemall.order.application.util.exception.product.LitemallGoodsServiceUnavailableException unavailable) {
                throw unavailable;
            }
            throw new org.linlinjava.litemall.order.application.util.exception.product.LitemallGoodsServiceUnavailableException(
                    "failed to load goods for stock reservation", cause);
        }
    }

    private Map<LitemallGoodsId, LitemallGoodsAggregate> convertToDomainGoodsMap(Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap) {
        return goodsMap.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> new LitemallGoodsId(entry.getKey().getId()),
                        Map.Entry::getValue
                ));
    }

    private Map<LitemallGoodsId, LitemallGoodsAggregate> batchGetGoodsAggregates(Set<Integer> goodsIds) {
        // Single batch call through the goods ACL instead of N individual calls
        return goodsFacade.batchGetGoods(goodsIds);
    }

    private Map<LitemallGoodsProductId, LitemallGoodsProductAggregate> batchGetProductAggregates(Set<Integer> goodsIds) {
        // goods-management is goodsId-centric; fetch each goods' variants through the
        // ACL and key them by product id for the stock-validation lookups.
        Map<LitemallGoodsProductId, LitemallGoodsProductAggregate> productsMap = new java.util.HashMap<>();
        for (Integer goodsId : goodsIds) {
            for (LitemallGoodsProductAggregate product : goodsFacade.getProductsByGoods(new LitemallGoodsId(goodsId))) {
                productsMap.put(product.getGoodsProductId(), product);
            }
        }
        return productsMap;
    }

    private void validateStockForAllItems(List<LitemallCartAggregate> cartList, AggregatesValidationContext context){
        List<String> outOfStockItems = new ArrayList<>();

        for (LitemallCartAggregate cartItem : cartList) {
            LitemallGoodsProductAggregate product = context.getProduct(cartItem.getProductId());

            int remainNumber = product.getNumber() - cartItem.getNumber();
            if (remainNumber < 0) {
                LitemallGoodsAggregate goods = context.getGoods(cartItem.getGoodsId());
                outOfStockItems.add(String.format(
                        "Product %s (ID: %d): requested %d, available %d",
                        goods.getGoodsName(),
                        product.getGoodsProductId().getId(),
                        cartItem.getNumber(),
                        product.getNumber()
                ));
            }
        }

        if (!outOfStockItems.isEmpty()) {
            String errorMessage = "Insufficient stock for products:\n" +
                    String.join("\n", outOfStockItems);
            throw new org.linlinjava.litemall.order.application.util.exception.product.LitemallInsufficientStockException(errorMessage);
        }
    }

    private void reduceStockForAllItems(List<LitemallCartAggregate> cartList, AggregatesValidationContext context){

        // Prepare batch reduce stock requests (product id -> requested quantity)
        Map<Integer, Integer> productQuantities = cartList.stream()
                .collect(Collectors.toMap(
                        cartItem -> cartItem.getProductId().getId(),
                        LitemallCartAggregate::getNumber,
                        Integer::sum
                ));

        // Single batch reserve/reduce through the goods ACL
        Map<Integer, Boolean> reduceResults = goodsFacade.reduceStock(productQuantities);

        // Verify EVERY requested product was confirmed reduced. Treat a missing key
        // (null) the same as an explicit false — a degraded/partial response must
        // never be read as "all reduced".
        List<Integer> failedReductions = productQuantities.keySet().stream()
                .filter(productId -> !Boolean.TRUE.equals(reduceResults.get(productId)))
                .toList();

        if (!failedReductions.isEmpty()) {
            // Any product that DID confirm is now an orphaned remote decrement: the
            // compensating restore is a known no-op (goods-management has no restore
            // endpoint — see docs/handoff-goods-management-defects.md Defect 3), so
            // record the exact ledger for ops reconciliation before aborting.
            Map<Integer, Integer> confirmedReductions = productQuantities.entrySet().stream()
                    .filter(e -> Boolean.TRUE.equals(reduceResults.get(e.getKey())))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            if (!confirmedReductions.isEmpty()) {
                log.error("Partial stock reservation: products {} were decremented in "
                        + "goods-management but the placement is aborting (failed: {}). "
                        + "Compensation is a no-op — reconcile manually (productId -> qty): {}",
                        confirmedReductions.keySet(), failedReductions, confirmedReductions);
            }
            throw new org.linlinjava.litemall.order.application.util.exception.product.LitemallGoodsServiceUnavailableException(
                    "stock reservation unconfirmed for product IDs " + failedReductions);
        }

        // The remote reserve has now committed in goods-management. If THIS local
        // transaction subsequently rolls back, the decrement would be orphaned, so
        // register a compensating release on rollback (best-effort — see
        // LitemallGoodsFacade.restoreStock).
        registerStockRestoreOnRollback(productQuantities);
    }

    /**
     * Register a transaction-synchronization that releases the just-reserved stock
     * if (and only if) the surrounding transaction rolls back. No-op when there is
     * no active transaction.
     */
    private void registerStockRestoreOnRollback(Map<Integer, Integer> productQuantities) {
        if (productQuantities.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    log.warn("Order placement rolled back after stock reserve; compensating restore for {}",
                            productQuantities);
                    goodsFacade.restoreStock(productQuantities);
                }
            }
        });
    }

    /**
     * Register a transaction-synchronization that gives a just-redeemed coupon back
     * to the customer if (and only if) the surrounding placement transaction rolls
     * back — otherwise the failed placement would eat the coupon. Release is
     * idempotent and replay-safe on the promotion side; a failure here is logged by
     * the facade for manual replay, never rethrown from a rollback hook.
     */
    private void registerCouponReleaseOnRollback(LitemallUserId userId, Integer userCouponId,
                                                 LitemallOrderId orderId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    log.warn("Order placement rolled back after coupon redeem; releasing user coupon {} (order {})",
                            userCouponId, orderId.getId());
                    promotionFacade.releaseCoupon(userId, userCouponId, orderId);
                }
            }
        });
    }

    /**
     * If the cancelled order had consumed a coupon, give it back once the cancel
     * COMMITS (a rolled-back cancel must not release). The order row doesn't carry
     * the userCouponId, so it is looked up at promotion by order id; both the lookup
     * and the release are best-effort — promotion's release is replay-safe, so a
     * miss only means the coupon stays consumed until replayed.
     */
    private void releaseCouponOnCancelCommit(LitemallOrderAggregate order) {
        if (order.getCouponPrice() == null || order.getCouponPrice().getAmount() == null
                || order.getCouponPrice().getAmount().signum() <= 0
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        LitemallUserId userId = order.getUserId();
        LitemallOrderId orderId = order.getOrderId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                promotionFacade.findRedeemedUserCouponForOrder(userId, orderId)
                        .ifPresent(userCouponId -> promotionFacade.releaseCoupon(userId, userCouponId, orderId));
            }
        });
    }

    public LitemallGrouponRepository getGrouponRepository() {
        return this.grouponServiceLayer.getGrouponRepository();
    }


}
