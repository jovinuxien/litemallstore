package org.linlinjava.litemall.order.application.internal.cj;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException;
import org.linlinjava.litemall.order.application.util.exception.order.LitemallOrderServiceException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link CjOrderAvailabilityChecker}: a CJ submit is blocked (422) when any line has no
 * resolvable {@code cj_vid}, passes when every line resolves, and the customer-facing
 * message names the affected goods (never the internal "no cj_vid on the row" text).
 */
@ExtendWith(MockitoExtension.class)
class CjOrderAvailabilityCheckerTest {

    @Mock
    private CjOrderLineResolver lineResolver;

    @InjectMocks
    private CjOrderAvailabilityChecker checker;

    private LitemallCartAggregate cartLine(int productId, String goodsName) {
        LitemallCartAggregate line = new LitemallCartAggregate();
        line.setProductId(new LitemallGoodsProductId(productId));
        line.setGoodsName(goodsName);
        return line;
    }

    @Test
    void allLinesResolve_passes() {
        when(lineResolver.resolveVid(11)).thenReturn("vid-11");
        when(lineResolver.resolveVid(22)).thenReturn("vid-22");

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
}
