package org.linlinjava.litemall.order.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Freight-template feature flags (Wave 4, Task A — docs/adr-freight-templates.md).
 *
 * <p>{@code freight.template.enabled} — master switch. {@code false} (the code default)
 * bypasses template resolution entirely: quote and submit fall back to the legacy flat
 * {@code litemall_express_freight_min/value} rule, byte-identical to pre-Wave-4 behavior.
 * The dev config enables it.
 *
 * <p>{@code freight.template.combine-mode} — how per-template freight groups of one cart
 * combine: {@code max} (default — charge the most expensive group; multi-template carts
 * never pay more than the worst single template) or {@code sum} (crmeb-exact — adds the
 * groups; silently raises multi-template carts, hence not the default).
 */
@Component
@ConfigurationProperties(prefix = "freight.template")
public class FreightTemplateProperties {

    public static final String COMBINE_MAX = "max";
    public static final String COMBINE_SUM = "sum";

    /** Master switch; false = legacy flat rule only (safe default). */
    private boolean enabled = false;

    /** "max" (default) or "sum" (crmeb-exact). Unknown values behave as "max". */
    private String combineMode = COMBINE_MAX;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCombineMode() {
        return combineMode;
    }

    public void setCombineMode(String combineMode) {
        this.combineMode = combineMode;
    }

    public boolean isSumMode() {
        return COMBINE_SUM.equalsIgnoreCase(combineMode);
    }
}
