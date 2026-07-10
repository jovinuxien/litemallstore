package org.linlinjava.litemall.order.application.internal.cj;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException;
import org.linlinjava.litemall.order.application.util.exception.order.LitemallOrderServiceException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjStockFacade;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CjOrderAvailabilityChecker}: a CJ submit is blocked (422) when any line has no
 * resolvable {@code cj_vid} OR requests more than CJ positively reports in stock; it passes
 * when every line resolves and the stock answer is sufficient or UNKNOWN (advisory check —
 * CJ down never blocks checkout). Customer-facing messages name the affected goods.
 */
@ExtendWith(MockitoExtension.class)
class CjOrderAvailabilityCheckerTest {

    @Mock
    private CjOrderLineResolver lineResolver;
    @Mock
    private CjStockFacade stockFacade;

    @InjectMocks
    private CjOrderAvailabilityChecker checker;

    private LitemallCartAggregate cartLine(int productId, String goodsName) {
        return cartLine(productId, goodsName, 1);
    }

    private LitemallCartAggregate cartLine(int productId, String goodsName, int quantity) {
        LitemallCartAggregate line = new LitemallCartAggregate();
        line.setProductId(new LitemallGoodsProductId(productId));
        line.setGoodsName(goodsName);
        line.setNumber(quantity);
        return line;
    }

    @Test
    void allLinesResolve_passes() {
        when(lineResolver.resolveVid(11)).thenReturn("vid-11");
        when(lineResolver.resolveVid(22)).thenReturn("vid-22");
        when(stockFacade.availableStock(anyString())).thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
                checker.assertAllFulfillable(List.of(cartLine(11, "Lamp"), cartLine(22, "Cable"))));
    }

    @Test
    void vidlessLine_isBlockedWithFriendlyMessage() {
        when(lineResolver.resolveVid(11)).thenReturn("vid-11");
        when(lineResolver.resolveVid(22))
                .thenThrow(new LitemallCjOrderException("CJ product 22 (goods 999) has no cj_vid on the row"));

        LitemallOrderServiceException ex = assertThrows(LitemallOrderServiceException.class,
                () -> checker.assertAllFulfillable(List.of(cartLine(11, "Lamp"), cartLine(22, "Light Box"))));
        assertTrue(ex.getMessage().contains("Light Box"), "names the affected goods");
        assertTrue(ex.getMessage().toLowerCase().contains("temporarily unavailable"), "customer-facing wording");
        assertTrue(!ex.getMessage().contains("cj_vid"), "hides the internal cj_vid detail");
        // an unresolvable line blocks BEFORE any stock lookup is spent on the cart
        verify(stockFacade, never()).availableStock(anyString());
    }

    @Test
    void aggregatesAllUnavailableLines() {
        when(lineResolver.resolveVid(11))
                .thenThrow(new LitemallCjOrderException("no cj_vid"));
        when(lineResolver.resolveVid(22))
                .thenThrow(new LitemallCjOrderException("no cj_vid"));

        LitemallOrderServiceException ex = assertThrows(LitemallOrderServiceException.class,
                () -> checker.assertAllFulfillable(List.of(cartLine(11, "Lamp"), cartLine(22, "Cable"))));
        assertTrue(ex.getMessage().contains("Lamp") && ex.getMessage().contains("Cable"),
                "reports every affected line, not just the first");
    }

    @Test
    void nullListAndNullLines_areIgnored() {
        assertDoesNotThrow(() -> checker.assertAllFulfillable(null));
        assertDoesNotThrow(() -> checker.assertAllFulfillable(Arrays.asList((LitemallCartAggregate) null)));
    }

    // ---- Wave 3: live stock check -----------------------------------------------------

    @Test
    void requestedAboveCjStock_isBlockedNamingLineAndFigures() {
        when(lineResolver.resolveVid(11)).thenReturn("vid-11");
        when(stockFacade.availableStock("vid-11")).thenReturn(Optional.of(2));

        LitemallOrderServiceException ex = assertThrows(LitemallOrderServiceException.class,
                () -> checker.assertAllFulfillable(List.of(cartLine(11, "Nordic Mug", 5))));
        assertTrue(ex.getMessage().contains("Nordic Mug"), "names the offending line");
        assertTrue(ex.getMessage().contains("requested 5") && ex.getMessage().contains("only 2"),
                "carries the requested/available figures");
    }

    @Test
    void requestedWithinCjStock_passes() {
        when(lineResolver.resolveVid(11)).thenReturn("vid-11");
        when(stockFacade.availableStock("vid-11")).thenReturn(Optional.of(5));

        assertDoesNotThrow(() -> checker.assertAllFulfillable(List.of(cartLine(11, "Nordic Mug", 5))));
    }

    @Test
    void unknownStock_passes_advisoryCheck() {
        when(lineResolver.resolveVid(11)).thenReturn("vid-11");
        when(stockFacade.availableStock("vid-11")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> checker.assertAllFulfillable(List.of(cartLine(11, "Nordic Mug", 999))));
    }

    @Test
    void sameVariantOnSeveralLines_isComparedAsTheSum() {
        // two products mapping to the SAME CJ variant: 3 + 3 requested > 4 in stock
        when(lineResolver.resolveVid(11)).thenReturn("vid-shared");
        when(lineResolver.resolveVid(22)).thenReturn("vid-shared");
        when(stockFacade.availableStock("vid-shared")).thenReturn(Optional.of(4));

        LitemallOrderServiceException ex = assertThrows(LitemallOrderServiceException.class,
                () -> checker.assertAllFulfillable(
                        List.of(cartLine(11, "Mug (blue)", 3), cartLine(22, "Mug (red)", 3))));
        assertTrue(ex.getMessage().contains("requested 6"), "aggregates the per-variant quantity");
    }
}
