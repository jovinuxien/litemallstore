package org.linlinjava.litemall.promotion.application.ports;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Outbound port for the Wave-20 DIY-page publish source: goods-management's
 * PUBLIC page read ({@code GET /srv/page/{id}} through the configured
 * {@code goods.service.url} — no machine token, the path is on public-paths).
 *
 * <p>Implemented in {@code infrastructure/acl/goods}. Semantics:
 * <ul>
 *   <li>ACTIVE page ⇒ present {@link PromoPage};</li>
 *   <li>not active / unknown (goods-management errno 642) ⇒ empty — the
 *       caller turns that into its typed "page not active" errno;</li>
 *   <li>transport failure / unexpected envelope ⇒
 *       {@link PromoPageGatewayException} — never a silent fallback.</li>
 * </ul>
 */
public interface PromoPagePort {

    /** The ACTIVE page, or empty when goods-management answers errno 642. */
    Optional<PromoPage> fetchActive(Integer pageId);

    /**
     * @param category {@code general | coupon | groupon}; pre-V54 responses carry
     *                 no category field — the adapter defaults it to {@code general}
     * @param components ordered as rendered; {@code config} is the component's raw
     *                   config JSON as nested maps/lists (shape varies per type)
     */
    record PromoPage(Integer id, String name, String category, List<PageComponent> components) {
    }

    record PageComponent(String type, Map<String, Object> config) {
    }

    /** Page read failed for a reason other than "not active" — surface, never guess. */
    class PromoPageGatewayException extends RuntimeException {
        public PromoPageGatewayException(String message) {
            super(message);
        }

        public PromoPageGatewayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
