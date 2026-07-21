package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderPlacement;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderResult;
import org.linlinjava.litemall.order.infrastructure.services.cj.CjTokenService;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.CjOrderFeignClient;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjCreateOrderRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjCreateOrderResponse;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the CJ ACL. Asserts the facade maps a {@link CjOrderPlacement} to the CJ request,
 * unwraps a successful response, and converts a CJ {@code result=false} business error AND a
 * transport failure into {@link LitemallCjOrderException} — so a CJ outage fails the placement
 * cleanly. Mirrors {@code LitemallGoodsFacadeImplTest}.
 *
 * <p>Only the {@link CjOrderFeignClient} INTERFACE is Mockito-mocked; {@link CjTokenService} (a
 * concrete class) is hand-stubbed via a subclass so the test stays clear of the
 * Mockito-2.x/JDK-21 subclass-mock-maker issue (pre-existing root-pom debt).
 */
@ExtendWith(MockitoExtension.class)
class CjDropshipOrderFacadeImplTest {

    @Mock
    private CjOrderFeignClient cjOrderFeignClient;

    /** Hand-stubbed concrete token service (avoids subclass-mocking a concrete class). */
    private final CjTokenService tokenStub = new CjTokenService(null, "e@x", "k", true) {
        @Override
        public String getValidToken() {
            return "tok";
        }
    };

    private CjDropshipOrderFacadeImpl facade() {
        return new CjDropshipOrderFacadeImpl(cjOrderFeignClient, tokenStub, "CN", "CJPacket Ordinary",
                true, 0, "", "orders@litemall.dev");
    }

    private static CjOrderPlacement placement() {
        return CjOrderPlacement.builder()
                .orderNumber("ORD-1")
                .customerName("Jane")
                .phone("15555550123")
                .country("United States")
                .countryCode("US")
                .lines(List.of(CjOrderPlacement.Line.builder().vid("VID-9").quantity(2).build()))
                .build();
    }

    @Test
    void placeOrder_mapsRequestAndUnwrapsSuccess() {
        CjCreateOrderResponse ok = new CjCreateOrderResponse();
        ok.setResult(true);
        CjCreateOrderResponse.Data data = new CjCreateOrderResponse.Data();
        data.setOrderId("CJ-100");
        data.setOrderNum("CJ-NUM-100");
        data.setOrderStatus("CREATED");
        ok.setData(data);

        ArgumentCaptor<CjCreateOrderRequest> captor = ArgumentCaptor.forClass(CjCreateOrderRequest.class);
        when(cjOrderFeignClient.createOrderV2(eq("tok"), captor.capture())).thenReturn(ok);

        CjOrderResult result = facade().placeOrder(placement());

        assertEquals("CJ-100", result.getCjOrderId());
        assertEquals("CJ-NUM-100", result.getCjOrderNum());
        assertEquals("CREATED", result.getCjOrderStatus());
        CjCreateOrderRequest sent = captor.getValue();
        assertEquals("ORD-1", sent.getOrderNumber());
        assertEquals("Jane", sent.getShippingCustomerName());
        assertEquals(1, sent.getProducts().size());
        assertEquals("VID-9", sent.getProducts().get(0).getVid());
        assertEquals(2, sent.getProducts().get(0).getQuantity());
        // Wave 3: create-only draft (no CJ money inside the pay TX) + sandbox flag from config
        assertEquals(3, sent.getPayType());
        assertEquals(1, sent.getIsSandbox());
    }

    @Test
    void placeOrder_resultFalse_throwsCjOrderException() {
        CjCreateOrderResponse rejected = new CjCreateOrderResponse();
        rejected.setResult(false);
        rejected.setMessage("fromCountryCode must not be empty");
        when(cjOrderFeignClient.createOrderV2(eq("tok"), any())).thenReturn(rejected);

        LitemallCjOrderException ex = assertThrows(LitemallCjOrderException.class,
                () -> facade().placeOrder(placement()));
        assertTrue(ex.getMessage().contains("fromCountryCode"));
    }

    @Test
    void placeOrder_transportFailure_throwsRetryable() {
        when(cjOrderFeignClient.createOrderV2(eq("tok"), any()))
                .thenThrow(new RuntimeException("connect timed out"));

        // Wave 8: transport-level failures are RETRYABLE — the placement sweep keeps the
        // paid order queued instead of a terminal park.
        assertThrows(org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjRetryableException.class,
                () -> facade().placeOrder(placement()));
    }

    @Test
    void placeOrder_acceptedButUnparseableData_throwsRetryable_neverANullIdResult() {
        // CJ accepted (result=true) but data didn't bind. Pre-Wave-8 this returned a
        // CjOrderResult with a null cjOrderId, producing a stuck paid order no guard could
        // touch. Now: retryable — the sweep reconciles by orderNumber and adopts the order.
        CjCreateOrderResponse accepted = new CjCreateOrderResponse();
        accepted.setResult(true);
        accepted.setData(null);
        when(cjOrderFeignClient.createOrderV2(eq("tok"), any())).thenReturn(accepted);

        assertThrows(org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjRetryableException.class,
                () -> facade().placeOrder(placement()));
    }

    @Test
    void placeOrder_rateLimitRejection_isRetryable_notTerminal() {
        CjCreateOrderResponse limited = new CjCreateOrderResponse();
        limited.setResult(false);
        limited.setMessage("Too many requests, please try again later");
        when(cjOrderFeignClient.createOrderV2(eq("tok"), any())).thenReturn(limited);

        assertThrows(org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjRetryableException.class,
                () -> facade().placeOrder(placement()));
    }

    @Test
    void placeOrder_noLines_throwsWithoutCallingCj() {
        CjOrderPlacement empty = CjOrderPlacement.builder().orderNumber("ORD-2").lines(List.of()).build();

        assertThrows(LitemallCjOrderException.class, () -> facade().placeOrder(empty));
        verify(cjOrderFeignClient, never()).createOrderV2(any(), any());
    }
}
