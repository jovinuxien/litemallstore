package org.linlinjava.litemall.order.application.internal.cj;

import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallAddressRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjDropshipOrderFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderPlacement;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pay-first CJ fulfillment: once a {@code source='cj'} order is PAID, replay it to CJ
 * {@code createOrder} through the {@link CjDropshipOrderFacade} ACL and record the CJ
 * identifiers on the order row. Called INSIDE the payment transaction, so a CJ rejection
 * (thrown as {@link LitemallCjOrderException}) rolls back the wallet debit and the PAID
 * status together — no paid-but-unfulfillable order, and no CJ order the customer never
 * paid for. {@code order_sn} is the merchant orderNumber CJ dedupes on, capping the blast
 * radius of the rare commit-fails-after-CJ-accepted window.
 *
 * <p>The structured shipping address (province/city/zip as separate CJ fields) is
 * re-resolved from the address book via the {@code address_id} captured at submit — the
 * flattened {@code litemall_order.address} string cannot be split back apart. The address
 * book carries no country, so the destination country is the {@code countryCode} the
 * customer picked at checkout (persisted on the order), falling back to
 * {@code spring.cjdropship.api.ship-to-country-code} for callers that never sent one.
 */
@Service
public class CjFulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(CjFulfillmentService.class);

    /** CJ statuses in which {@code deleteOrder} is still worth attempting (money not yet moved). */
    private static final Set<String> CJ_DELETABLE_STATUSES = Set.of("CREATED", "IN_CART", "UNPAID");

    private final CjDropshipOrderFacade cjOrderFacade;
    private final CjOrderLineResolver lineResolver;
    private final LitemallAddressRepository addressRepository;
    private final LitemallOrderRepository orderRepository;
    private final LitemallOrderStatusHistoryRepository statusHistoryRepository;
    /** Deployment-market fallback destination country when the order carries none. */
    private final String defaultShipToCountryCode;

    public CjFulfillmentService(CjDropshipOrderFacade cjOrderFacade,
                                CjOrderLineResolver lineResolver,
                                LitemallAddressRepository addressRepository,
                                LitemallOrderRepository orderRepository,
                                LitemallOrderStatusHistoryRepository statusHistoryRepository,
                                @Value("${spring.cjdropship.api.ship-to-country-code:}") String defaultShipToCountryCode) {
        this.cjOrderFacade = cjOrderFacade;
        this.lineResolver = lineResolver;
        this.addressRepository = addressRepository;
        this.orderRepository = orderRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.defaultShipToCountryCode = defaultShipToCountryCode;
    }

    /**
     * Place the paid order at CJ and persist {@code cj_order_id}/{@code cj_order_num}.
     * Throws {@link LitemallCjOrderException} on any gap (no lines, unresolvable address,
     * missing destination country, CJ rejection) so the caller's transaction rolls back.
     */
    public CjOrderResult placeForPaidOrder(LitemallOrderAggregate order,
                                           List<LitemallOrderGoodsAggregate> orderGoods) {
        if (orderGoods == null || orderGoods.isEmpty()) {
            throw new LitemallCjOrderException(
                    "CJ order " + order.getOrderSn() + " has no order-goods lines to place");
        }
        LitemallAddressAggregate address = resolveAddress(order);
        String countryCode = StringUtils.hasText(order.getCountryCode())
                ? order.getCountryCode() : defaultShipToCountryCode;
        if (!StringUtils.hasText(countryCode)) {
            throw new LitemallCjOrderException(
                    "no destination country for CJ order " + order.getOrderSn()
                            + ": send countryCode at submit or set spring.cjdropship.api.ship-to-country-code");
        }

        List<CjOrderPlacement.Line> lines = orderGoods.stream()
                .map(g -> CjOrderPlacement.Line.builder()
                        .vid(lineResolver.resolveVid(g.getProductId().getId()))
                        .quantity(g.getNumber())
                        .build())
                .collect(Collectors.toList());

        CjOrderPlacement placement = CjOrderPlacement.builder()
                .orderNumber(order.getOrderSn()) // CJ-side idempotency key
                .customerName(order.getConsignee())
                .phone(order.getMobile())
                .countryCode(countryCode)
                // CJ createOrder validates shippingCountry (the NAME) as non-empty on top
                // of the code (error 1600300); we persist only the ISO code, so derive it.
                .country(countryNameFor(countryCode))
                .province(address.getProvince())
                .city(address.getCity())
                .address(joinNonBlank(address.getCounty(), address.getAddressDetail()))
                .zip(address.getPostalCode())
                .remark("")
                .lines(lines)
                .build();

        CjOrderResult result = cjOrderFacade.placeOrder(placement);
        // createOrderV2 (payType=3) leaves the CJ order a CREATED draft; the post-commit
        // hook / status-sync poller drive confirm + payBalance from this initial status.
        String initialCjStatus = StringUtils.hasText(result.getCjOrderStatus())
                ? result.getCjOrderStatus() : "CREATED";
        orderRepository.recordCjPlacement(order.getOrderId(), result.getCjOrderId(), result.getCjOrderNum(),
                result.getLogisticName(), initialCjStatus);
        log.info("CJ fulfillment placed for order {} (sn {}): cjOrderId={}, cjOrderNum={}, logistic={}",
                order.getOrderId().getId(), order.getOrderSn(),
                result.getCjOrderId(), result.getCjOrderNum(), result.getLogisticName());
        return result;
    }

    /**
     * Best-effort CJ-side cleanup when a local order stops before CJ fulfilment (cancel,
     * refund approved): delete the CJ order while CJ still allows it (CREATED / IN_CART;
     * UNPAID is attempted too per the Wave-3 contract — CJ refuses it harmlessly). No-op
     * for non-CJ orders and orders never placed at CJ (cancel-before-pay: pay-first means
     * there is nothing at CJ yet). Never throws — a refusal or outage is logged and the
     * order flagged for manual attention in the CJ dashboard.
     */
    public void cancelAtCjIfDeletable(LitemallOrderAggregate order, String context) {
        if (order == null || !order.isCjFulfilled() || !StringUtils.hasText(order.getCjOrderId())) {
            return;
        }
        String cjStatus = order.getCjOrderStatus();
        if (cjStatus != null && !CJ_DELETABLE_STATUSES.contains(cjStatus)) {
            log.info("CJ order {} (local {}) not deletable at CJ (status {}); skipping delete on {}",
                    order.getCjOrderId(), order.getOrderId().getId(), cjStatus, context);
            return;
        }
        if (cjOrderFacade.deleteOrder(order.getCjOrderId())) {
            orderRepository.updateCjOrderStatus(order.getOrderId(), "CANCELLED");
            LitemallOrderStatus local = order.getOrderStatus();
            statusHistoryRepository.record(new LitemallOrderStatusChange(
                    order.getOrderId(), local, local, CjLifecycleService.CHANGE_TYPE_CJ_SYNC,
                    "CJ order " + order.getCjOrderId() + " deleted at CJ (" + context + ")",
                    "system", LocalDateTime.now()));
            log.info("CJ order {} (local {}) deleted at CJ on {}",
                    order.getCjOrderId(), order.getOrderId().getId(), context);
        } else {
            log.warn("CJ order {} (local {}) could NOT be deleted at CJ on {} — check the CJ dashboard",
                    order.getCjOrderId(), order.getOrderId().getId(), context);
        }
    }

    private LitemallAddressAggregate resolveAddress(LitemallOrderAggregate order) {
        LitemallAddressAggregate address = order.getAddressId() != null
                ? addressRepository.findAddress(order.getUserId(), order.getAddressId())
                : addressRepository.findDefaultAddress(order.getUserId());
        if (address == null) {
            throw new LitemallCjOrderException(
                    "shipping address for CJ order " + order.getOrderSn()
                            + " is no longer resolvable (deleted between submit and pay?)");
        }
        return address;
    }

    /** English display name for an ISO country code ("SE" -> "Sweden"); falls back to the code. */
    private static String countryNameFor(String isoCode) {
        try {
            String name = new java.util.Locale.Builder().setRegion(isoCode.trim()).build()
                    .getDisplayCountry(java.util.Locale.ENGLISH);
            return name == null || name.isBlank() ? isoCode : name;
        } catch (RuntimeException e) {
            return isoCode;
        }
    }

    private static String joinNonBlank(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(part.trim());
            }
        }
        return sb.toString();
    }
}
