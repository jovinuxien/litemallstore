package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import java.util.Optional;

/**
 * Anti-corruption seam over the CJ live variant-stock lookup. Consumers (the submit-time
 * availability check) depend on THIS interface only — never the Feign client — mirroring
 * {@link CjDropshipOrderFacade}.
 *
 * <p>The answer is ADVISORY by contract: {@link Optional#empty()} means "unknown" (CJ
 * unreachable, breaker open, or an unusable response) and callers must treat it as
 * pass-through, never as zero stock. See docs/adr-cj-lifecycle-parity.md.
 */
public interface CjStockFacade {

    /**
     * Best available stock for a CJ variant id, from the warehouse placement would ship from
     * (the configured {@code from-country-code}) when CJ stocks it there, else the best
     * single-warehouse figure. Never throws.
     *
     * @return the available quantity, or empty when CJ gave no usable answer
     */
    Optional<Integer> availableStock(String vid);
}
