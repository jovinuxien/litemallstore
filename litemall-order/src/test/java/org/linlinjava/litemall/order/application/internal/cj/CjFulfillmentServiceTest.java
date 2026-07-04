package org.linlinjava.litemall.order.application.internal.cj;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallAddressRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallAddressId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjDropshipOrderFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderPlacement;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderResult;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CjFulfillmentService}: the paid order is replayed to CJ with {@code order_sn}
 * as the idempotent merchant orderNumber and the STRUCTURED address re-resolved from the
 * address book; the CJ ids are recorded on the row; and every gap (missing country,
 * vanished address) throws BEFORE the facade is called so the payment rolls back.
 */
@ExtendWith(MockitoExtension.class)
class CjFulfillmentServiceTest {

    @Mock
    private CjDropshipOrderFacade cjOrderFacade;
    @Mock
    private CjOrderLineResolver lineResolver;
    @Mock
    private LitemallAddressRepository addressRepository;
    @Mock
    private LitemallOrderRepository orderRepository;

    private CjFulfillmentService service(String defaultShipToCountry) {
        return new CjFulfillmentService(
                cjOrderFacade, lineResolver, addressRepository, orderRepository, defaultShipToCountry);
    }

    private LitemallOrderAggregate paidCjOrder(String countryCode) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(new LitemallOrderId(61));
        order.setUserId(new LitemallUserId(42));
        order.setOrderSn("20260704654321");
        order.setConsignee("Jovi Bimeni");
        order.setMobile("0700000000");
        order.setAddressId(new LitemallAddressId(7));
        order.setCountryCode(countryCode);
        order.setSource(LitemallOrderAggregate.SOURCE_CJ);
        return order;
    }

    private LitemallAddressAggregate bookAddress() {
        LitemallAddressAggregate address = new LitemallAddressAggregate();
        address.setProvince("Oslo");
        address.setCity("Oslo");
        address.setCounty("Frogner");
        address.setAddressDetail("Storgata 1");
        address.setPostalCode("0155");
        return address;
    }

    private LitemallOrderGoodsAggregate line(int productId, short quantity) {
        LitemallOrderGoodsAggregate goods = new LitemallOrderGoodsAggregate();
        goods.setProductId(new LitemallGoodsProductId(productId));
        goods.setNumber(quantity);
        return goods;
    }

    @Test
    void placesWithOrderSnStructuredAddressAndResolvedVids_thenRecordsCjIds() {
        LitemallOrderAggregate order = paidCjOrder("NO");
        when(addressRepository.findAddress(order.getUserId(), order.getAddressId()))
                .thenReturn(bookAddress());
        when(lineResolver.resolveVid(1043)).thenReturn("vid-abc");
        when(cjOrderFacade.placeOrder(any()))
                .thenReturn(new CjOrderResult("cj-id-1", "cj-num-1", "CREATED"));

        service("").placeForPaidOrder(order, List.of(line(1043, (short) 2)));

        ArgumentCaptor<CjOrderPlacement> placement = ArgumentCaptor.forClass(CjOrderPlacement.class);
        verify(cjOrderFacade).placeOrder(placement.capture());
        CjOrderPlacement sent = placement.getValue();
        assertEquals("20260704654321", sent.getOrderNumber()); // order_sn = CJ idempotency key
        assertEquals("NO", sent.getCountryCode());
        assertEquals("Oslo", sent.getProvince());
        assertEquals("Frogner Storgata 1", sent.getAddress());
        assertEquals("0155", sent.getZip());
        assertEquals(1, sent.getLines().size());
        assertEquals("vid-abc", sent.getLines().get(0).getVid());
        assertEquals(2, sent.getLines().get(0).getQuantity());

        verify(orderRepository).recordCjPlacement(order.getOrderId(), "cj-id-1", "cj-num-1");
    }

    @Test
    void missingCountryEverywhere_failsBeforeCallingCj() {
        LitemallOrderAggregate order = paidCjOrder(null);
        when(addressRepository.findAddress(order.getUserId(), order.getAddressId()))
                .thenReturn(bookAddress());

        assertThrows(LitemallCjOrderException.class,
                () -> service("").placeForPaidOrder(order, List.of(line(1043, (short) 1))));

        verify(cjOrderFacade, never()).placeOrder(any());
        verify(orderRepository, never()).recordCjPlacement(any(), any(), any());
    }

    @Test
    void orderWithoutCountry_fallsBackToTheConfiguredShipToCountry() {
        LitemallOrderAggregate order = paidCjOrder(null);
        when(addressRepository.findAddress(order.getUserId(), order.getAddressId()))
                .thenReturn(bookAddress());
        when(lineResolver.resolveVid(1043)).thenReturn("vid-abc");
        when(cjOrderFacade.placeOrder(any()))
                .thenReturn(new CjOrderResult("cj-id-2", "cj-num-2", "CREATED"));

        service("SE").placeForPaidOrder(order, List.of(line(1043, (short) 1)));

        ArgumentCaptor<CjOrderPlacement> placement = ArgumentCaptor.forClass(CjOrderPlacement.class);
        verify(cjOrderFacade).placeOrder(placement.capture());
        assertEquals("SE", placement.getValue().getCountryCode());
    }

    @Test
    void vanishedAddress_failsBeforeCallingCj() {
        LitemallOrderAggregate order = paidCjOrder("NO");
        when(addressRepository.findAddress(order.getUserId(), order.getAddressId()))
                .thenReturn(null);

        assertThrows(LitemallCjOrderException.class,
                () -> service("").placeForPaidOrder(order, List.of(line(1043, (short) 1))));

        verify(cjOrderFacade, never()).placeOrder(any());
    }

    @Test
    void orderWithoutAddressId_usesTheDefaultAddress() {
        LitemallOrderAggregate order = paidCjOrder("NO");
        order.setAddressId(null); // legacy row from before V27
        when(addressRepository.findDefaultAddress(order.getUserId())).thenReturn(bookAddress());
        when(lineResolver.resolveVid(1043)).thenReturn("vid-abc");
        when(cjOrderFacade.placeOrder(any()))
                .thenReturn(new CjOrderResult("cj-id-3", "cj-num-3", "CREATED"));

        service("").placeForPaidOrder(order, List.of(line(1043, (short) 1)));

        verify(addressRepository).findDefaultAddress(order.getUserId());
        verify(cjOrderFacade).placeOrder(any());
    }
}
