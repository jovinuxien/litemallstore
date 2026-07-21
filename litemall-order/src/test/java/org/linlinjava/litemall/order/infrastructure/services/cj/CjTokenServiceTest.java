package org.linlinjava.litemall.order.infrastructure.services.cj;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjDisabledException;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.CjAuthFeignClient;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Wave-8 disabled seam on {@link CjTokenService}: both credentials blank ⇒ DISABLED —
 * {@code getValidToken} throws the typed {@link LitemallCjDisabledException} with ZERO
 * network calls (no auth hammering, no WARN spam); half-configured ⇒ refuses to construct
 * (deployment mistake, mirrors the Stripe seam); both set ⇒ enabled.
 */
@ExtendWith(MockitoExtension.class)
class CjTokenServiceTest {

    @Mock
    private CjAuthFeignClient authClient;

    @Test
    void bothBlank_isDisabled_andThrowsTypedWithoutAnyAuthCall() {
        CjTokenService service = new CjTokenService(authClient, "", "", false);

        assertFalse(service.isEnabled());
        assertThrows(LitemallCjDisabledException.class, service::getValidToken);
        // The whole point: a disabled deployment never even attempts CJ auth.
        verifyNoMoreInteractions(authClient);
    }

    @Test
    void halfConfigured_keyWithoutEmail_failsConstruction() {
        assertThrows(IllegalStateException.class,
                () -> new CjTokenService(authClient, "", "some-key", false));
    }

    @Test
    void halfConfigured_emailWithoutKey_failsConstruction() {
        assertThrows(IllegalStateException.class,
                () -> new CjTokenService(authClient, "shop@example.com", "", false));
    }

    @Test
    void bothSet_isEnabled() {
        CjTokenService service = new CjTokenService(authClient, "shop@example.com", "some-key", true);

        assertTrue(service.isEnabled());
    }
}
