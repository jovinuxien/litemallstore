package org.linlinjava.litemall.order.application.internal.cj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Wave 23 (V59): single source of truth for the CJ placement gating mode.
 *
 * <p>{@code litemall.order.cj.placement-mode} (env {@code LITEMALL_ORDER_CJ_PLACEMENT_MODE}):
 * <ul>
 *   <li>{@code manual} (DEFAULT): paid CJ orders wait in the durable placement queue until an
 *       admin approves them ({@code cj_placement_approved_time} stamp); the sweep only visits
 *       approved orders and {@link CjPlacementService#place} refuses unapproved ones (defence
 *       in depth against the pay-path fast placement).</li>
 *   <li>{@code auto}: pre-Wave-23 behavior — every paid CJ order places as soon as CJ is
 *       configured, no approval needed.</li>
 * </ul>
 *
 * <p>Fail-safe: any value other than the literal {@code auto} is treated as {@code manual} —
 * a typo in the env must never silently start placing unapproved orders at CJ.
 */
@Component
public class CjPlacementMode {

    private static final Logger log = LoggerFactory.getLogger(CjPlacementMode.class);

    private final boolean manual;

    public CjPlacementMode(@Value("${litemall.order.cj.placement-mode:manual}") String mode) {
        String normalized = mode == null ? "" : mode.trim().toLowerCase();
        this.manual = !"auto".equals(normalized);
        if (!this.manual) {
            log.info("CJ placement mode: AUTO — paid CJ orders place without admin approval");
        } else {
            log.info("CJ placement mode: MANUAL — paid CJ orders wait for admin approval before placement"
                    + ("manual".equals(normalized) || normalized.isEmpty()
                    ? "" : " (unrecognized mode '" + mode + "' treated as manual)"));
        }
    }

    /** True when placement requires an admin approval stamp (the default). */
    public boolean isManual() {
        return manual;
    }
}
