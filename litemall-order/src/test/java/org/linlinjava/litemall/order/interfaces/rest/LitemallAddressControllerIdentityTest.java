package org.linlinjava.litemall.order.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.application.internal.LitemallAddressServiceLayer;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.interfaces.dtos.address.AddressSaveRequest;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the address-book edge binds the owner from the gateway-trusted
 * X-User-Id header on every operation (no caller-supplied userId — the identity
 * rule that closed the cart IDOR pattern) and that /save returns the persisted id
 * the SPA feeds into POST /srv/order/submit.
 */
class LitemallAddressControllerIdentityTest {

    @Test
    void list_scopesToHeaderUser() {
        LitemallAddressServiceLayer service = mock(LitemallAddressServiceLayer.class);
        when(service.list(any())).thenReturn(List.of());
        LitemallAddressController controller = new LitemallAddressController(service);

        controller.list(42);

        ArgumentCaptor<LitemallUserId> cap = ArgumentCaptor.forClass(LitemallUserId.class);
        verify(service).list(cap.capture());
        assertEquals(42, cap.getValue().getId());
    }

    @Test
    void save_bindsOwnerFromHeader_andReturnsPersistedId() {
        LitemallAddressServiceLayer service = mock(LitemallAddressServiceLayer.class);
        when(service.save(any(), any())).thenReturn(77);
        LitemallAddressController controller = new LitemallAddressController(service);

        AddressSaveRequest body = new AddressSaveRequest();
        body.setName("Jane");
        body.setProvince("Littoral");
        body.setAddressDetail("12 Main St");

        ApiResponse<Integer> response = controller.save(99, body);

        assertEquals(0, response.getErrno());
        assertEquals(77, response.getData());        // /save surfaces the persisted id

        ArgumentCaptor<LitemallUserId> owner = ArgumentCaptor.forClass(LitemallUserId.class);
        ArgumentCaptor<LitemallAddressAggregate> agg = ArgumentCaptor.forClass(LitemallAddressAggregate.class);
        verify(service).save(owner.capture(), agg.capture());
        assertEquals(99, owner.getValue().getId());              // header identity wins
        assertEquals(99, agg.getValue().getUserId().getId());    // aggregate owner = header user
    }

    @Test
    void detail_scopesToHeaderUser() {
        LitemallAddressServiceLayer service = mock(LitemallAddressServiceLayer.class);
        when(service.detail(any(), any())).thenReturn(null);
        LitemallAddressController controller = new LitemallAddressController(service);

        ApiResponse<?> response = controller.detail(7, 123);

        // Not the caller's address (or absent) → 605 envelope, no cross-user read.
        assertEquals(605, response.getErrno());
        ArgumentCaptor<LitemallUserId> cap = ArgumentCaptor.forClass(LitemallUserId.class);
        verify(service).detail(cap.capture(), any());
        assertEquals(7, cap.getValue().getId());
    }

    @Test
    void delete_requiresId_andScopesToHeaderUser() {
        LitemallAddressServiceLayer service = mock(LitemallAddressServiceLayer.class);
        LitemallAddressController controller = new LitemallAddressController(service);

        AddressSaveRequest missingId = new AddressSaveRequest();
        assertEquals(402, controller.delete(7, missingId).getErrno());

        AddressSaveRequest body = new AddressSaveRequest();
        body.setId(5);
        assertEquals(0, controller.delete(7, body).getErrno());
        ArgumentCaptor<LitemallUserId> cap = ArgumentCaptor.forClass(LitemallUserId.class);
        ArgumentCaptor<org.linlinjava.litemall.order.domain.model.valueobjects.LitemallAddressId> idCap =
                ArgumentCaptor.forClass(org.linlinjava.litemall.order.domain.model.valueobjects.LitemallAddressId.class);
        verify(service).delete(cap.capture(), idCap.capture());
        assertEquals(7, cap.getValue().getId());
        assertEquals(5, idCap.getValue().getId());
    }
}
