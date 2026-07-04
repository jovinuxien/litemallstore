package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.application.util.exception.order.LitemallOrderServiceException;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallAddressRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCartRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.service.groupon.LitemallGrouponValidationResult;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the order-placement fix that lets a PLAIN order (no groupon, no coupon)
 * go through. Two regressions are covered:
 *
 * <ol>
 *   <li>Groupon and coupon are optional. {@code placeOrder} used to throw
 *       {@code IllegalArgumentException("The ... is required")} whenever any of
 *       {@code couponId / userCouponId / grouponRulesId / grouponLinkId} was null,
 *       which 500'd every normal checkout. Null now normalizes to the sentinel 0
 *       and a non-groupon order must never invoke groupon-rule validation (which
 *       throws "Groupon rules not found" for the non-existent rule 0).</li>
 *   <li>The groupon-rule validation call passed its three Integer args in the wrong
 *       order — {@code (userId, rulesId, linkId)} against a
 *       {@code (grouponRulesId, grouponLinkId, userId)} signature — corrupting
 *       every groupon order. The args must now line up with the signature.</li>
 * </ol>
 *
 * <p>Both tests short-circuit on a null checked-cart so they exercise only the
 * optional-selection handling without needing the full price/stock collaborators.
 */
@ExtendWith(MockitoExtension.class)
class LitemallOrderPlaceOptionalSelectionsTest {

    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private LitemallGrouponRepository grouponRepository;
    @Mock
    private LitemallCartRepository cartRepository;
    @Mock
    private LitemallAddressRepository addressRepository;
    @Mock
    private LitemallOrderGoodsRepository orderGoodsRepository;
    @Mock
    private LitemallCouponServiceLayer couponService;
    @Mock
    private LitemallDomainEventPublisher domainEventPublisher;

    // Field-injected (@Autowired) collaborators, not constructor args, so wire them
    // explicitly below (mirrors LitemallOrderPaidCancelTest).
    @Mock
    private LitemallCartServiceLayer cartServiceLayer;
    @Mock
    private LitemallGrouponServiceLayer grouponServiceLayer;

    @InjectMocks
    private LitemallOrderServiceImpl service;

    @BeforeEach
    void wireFieldInjectedDeps() {
        ReflectionTestUtils.setField(service, "cartServiceLayer", cartServiceLayer);
        ReflectionTestUtils.setField(service, "grouponServiceLayer", grouponServiceLayer);
        // placeOrder now hard-requires a resolvable shipping address before the cart
        // guard; give it one so both tests still reach the empty-cart short-circuit.
        when(addressRepository.findAddress(any(), any()))
                .thenReturn(new org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate());
    }

    @Test
    void placeOrder_withNullOptionalSelections_doesNotThrowAndSkipsGrouponValidation() throws Exception {
        LitemallPlaceOrderCommand command = new LitemallPlaceOrderCommand(
                42,    // userId
                null,  // cartId      -> 0 (whole checked cart)
                7,     // addressId
                null,  // couponId    -> 0 (none)
                null,  // userCouponId-> 0 (none)
                "please leave at door",
                null,  // grouponRulesId -> 0 (none)
                null,  // grouponLinkId  -> 0 (none)
                null); // countryCode (optional CJ destination)

        // A null/empty checked cart now fails placement with the dedicated
        // empty-cart exception right after the optional-selection handling — far
        // enough to prove the nulls were accepted (no IllegalArgumentException
        // about a "required" coupon/groupon).
        when(cartServiceLayer.getCheckedCartItems(any(), any())).thenReturn(null);

        assertThrows(LitemallOrderServiceException.class, () -> service.placeOrder(command));

        verify(grouponServiceLayer, never()).validateGrouponRules(anyInt(), anyInt(), anyInt());
    }

    @Test
    void placeOrder_withGroupon_validatesRulesWithArgsInSignatureOrder() throws Exception {
        LitemallPlaceOrderCommand command = new LitemallPlaceOrderCommand(
                42,  // userId
                0,   // cartId
                7,   // addressId
                0,   // couponId
                0,   // userCouponId
                "msg",
                5,   // grouponRulesId
                9,   // grouponLinkId
                null); // countryCode

        when(grouponServiceLayer.validateGrouponRules(5, 9, 42))
                .thenReturn(LitemallGrouponValidationResult.valid());
        when(cartServiceLayer.getCheckedCartItems(any(), any())).thenReturn(null);

        // Still short-circuits on the empty cart AFTER groupon validation ran.
        assertThrows(LitemallOrderServiceException.class, () -> service.placeOrder(command));

        // (grouponRulesId, grouponLinkId, userId) — NOT (userId, rulesId, linkId).
        verify(grouponServiceLayer).validateGrouponRules(5, 9, 42);
    }
}
