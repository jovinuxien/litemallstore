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

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /** Prod-shaped declared-amount policy: on, USD, the Wave-24 rate, EUR store. */
    private static CjDeclaredAmountConverter prodConverter() {
        return new CjDeclaredAmountConverter(true, "usd", new BigDecimal("0.866"), "eur");
    }

    private CjDropshipOrderFacadeImpl facade() {
        return facade(prodConverter());
    }

    private CjDropshipOrderFacadeImpl facade(CjDeclaredAmountConverter converter) {
        return new CjDropshipOrderFacadeImpl(cjOrderFeignClient, tokenStub, converter, "CN", "CJPacket Ordinary",
                true, 0, "", "orders@litemall.dev");
    }

    /** A paid-order placement as CjFulfillmentService builds it: EUR amounts + line ids. */
    private static CjOrderPlacement pricedPlacement() {
        return CjOrderPlacement.builder()
                .orderNumber("ORD-11")
                .customerName("Jovi")
                .phone("0700000000")
                .country("Sweden")
                .countryCode("SE")
                .declaredAmount(new BigDecimal("8.90"))
                .lines(List.of(CjOrderPlacement.Line.builder()
                        .vid("VID-9").quantity(2).unitPrice(new BigDecimal("4.45")).lineId(17).build()))
                .build();
    }

    private CjCreateOrderRequest capturePlacement(CjDropshipOrderFacadeImpl facade, CjOrderPlacement placement) {
        CjCreateOrderResponse ok = new CjCreateOrderResponse();
        ok.setResult(true);
        CjCreateOrderResponse.Data data = new CjCreateOrderResponse.Data();
        data.setOrderId("CJ-1");
        data.setOrderNum("CJ-NUM-1");
        data.setOrderStatus("CREATED");
        ok.setData(data);
        ArgumentCaptor<CjCreateOrderRequest> captor = ArgumentCaptor.forClass(CjCreateOrderRequest.class);
        when(cjOrderFeignClient.createOrderV2(eq("tok"), captor.capture())).thenReturn(ok);
        facade.placeOrder(placement);
        return captor.getValue();
    }

    @org.junit.jupiter.api.Test
    void accountWithoutEmail_sendsTheStoreFallback_andLogsIt() {
        try (org.linlinjava.litemall.order.testsupport.CapturedLogs logs =
                     new org.linlinjava.litemall.order.testsupport.CapturedLogs(CjDropshipOrderFacadeImpl.class)) {
            CjCreateOrderRequest request = capturePlacement(facade(), placement());

            org.assertj.core.api.Assertions.assertThat(request.getEmail()).isEqualTo("orders@litemall.dev");
            org.assertj.core.api.Assertions.assertThat(logs.warnings()).anyMatch(m ->
                    m.contains("ORD-1") && m.contains("store fallback address") && m.contains("orders@litemall.dev"));
        }
    }

    @org.junit.jupiter.api.Test
    void accountWithEmail_sendsTheCustomersOwn_andLogsNoFallback() {
        CjOrderPlacement withEmail = CjOrderPlacement.builder()
                .orderNumber("ORD-2").customerName("Jane").phone("15555550123")
                .country("United States").countryCode("US").email(" jane@example.com ")
                .lines(List.of(CjOrderPlacement.Line.builder().vid("VID-9").quantity(1).build()))
                .build();
        try (org.linlinjava.litemall.order.testsupport.CapturedLogs logs =
                     new org.linlinjava.litemall.order.testsupport.CapturedLogs(CjDropshipOrderFacadeImpl.class)) {
            CjCreateOrderRequest request = capturePlacement(facade(), withEmail);

            org.assertj.core.api.Assertions.assertThat(request.getEmail()).isEqualTo("jane@example.com");
            org.assertj.core.api.Assertions.assertThat(logs.warnings())
                    .noneMatch(m -> m.contains("store fallback address"));
        }
    }

    private static String json(CjCreateOrderRequest request) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request);
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

    // ---- Declared store amount → createOrderV2 shopAmount / unitPrice (plan D2 / D3 / D4) ----

    @Test
    void declaredAmount_usdMode_convertsEurToUsdAtTheInverseFxRate_2dpHalfUp() throws Exception {
        CjCreateOrderRequest sent = capturePlacement(facade(), pricedPlacement());

        assertEquals(new BigDecimal("10.28"), sent.getShopAmount());               // 8.90 / 0.866 = 10.277…
        assertEquals(new BigDecimal("5.14"), sent.getProducts().get(0).getUnitPrice()); // 4.45 / 0.866 = 5.138…
        assertEquals("17", sent.getProducts().get(0).getStoreLineItemId());
        String json = json(sent);
        assertTrue(json.contains("\"shopAmount\":10.28"), json);
        assertTrue(json.contains("\"unitPrice\":5.14"), json);
        assertTrue(json.contains("\"storeLineItemId\":\"17\""), json);
    }

    @Test
    void declaredAmount_storeMode_sendsTheStoreAmountUnchanged() {
        CjCreateOrderRequest sent = capturePlacement(
                facade(new CjDeclaredAmountConverter(true, "store", new BigDecimal("0.866"), "eur")),
                pricedPlacement());

        assertEquals(new BigDecimal("8.90"), sent.getShopAmount());
        assertEquals(new BigDecimal("4.45"), sent.getProducts().get(0).getUnitPrice());
    }

    @Test
    void declaredAmount_killSwitchOff_omitsEveryPriceField_requestIdenticalToBefore() throws Exception {
        CjCreateOrderRequest sent = capturePlacement(
                facade(new CjDeclaredAmountConverter(false, "usd", new BigDecimal("0.866"), "eur")),
                pricedPlacement());

        assertNull(sent.getShopAmount());
        assertNull(sent.getProducts().get(0).getUnitPrice());
        assertNull(sent.getProducts().get(0).getStoreLineItemId()); // reconciliation id rides the switch too
        String json = json(sent);
        assertFalse(json.contains("shopAmount"), json);
        assertFalse(json.contains("unitPrice"), json);
        assertFalse(json.contains("storeLineItemId"), json);
        // the pre-change line shape, byte for byte
        assertTrue(json.contains("\"products\":[{\"vid\":\"VID-9\",\"quantity\":2}]"), json);
    }

    @Test
    void declaredAmount_nullOnThePlacement_omitsShopAmountFromJson_nonNullProven() throws Exception {
        CjOrderPlacement noAmount = pricedPlacement();
        noAmount.setDeclaredAmount(null);

        CjCreateOrderRequest sent = capturePlacement(facade(), noAmount);

        assertNull(sent.getShopAmount());
        String json = json(sent);
        assertFalse(json.contains("shopAmount"), json);
        assertTrue(json.contains("\"unitPrice\":5.14"), json); // lines keep what they know
    }

    @Test
    void declaredAmount_nonPositiveFx_fallsBackToIdentity_notZeroOrInfinity() {
        CjCreateOrderRequest sent = capturePlacement(
                facade(new CjDeclaredAmountConverter(true, "usd", BigDecimal.ZERO, "eur")),
                pricedPlacement());

        assertEquals(new BigDecimal("8.90"), sent.getShopAmount());
        assertEquals(new BigDecimal("4.45"), sent.getProducts().get(0).getUnitPrice());
    }

    @Test
    void declaredAmount_manualControllerPlacementWithoutPrices_sendsNoPriceFields() throws Exception {
        // LitemallCjOrderController.toPlacement never sets declaredAmount / unitPrice / lineId
        CjCreateOrderRequest sent = capturePlacement(facade(), placement());

        assertNull(sent.getShopAmount());
        assertNull(sent.getProducts().get(0).getUnitPrice());
        assertNull(sent.getProducts().get(0).getStoreLineItemId());
        String json = json(sent);
        assertFalse(json.contains("shopAmount"), json);
        assertFalse(json.contains("unitPrice"), json);
    }
}
