package org.linlinjava.litemall.promotion.interfaces.dtos;

import java.math.BigDecimal;

/**
 * Wave-22 deliver-to-segment request body. Every criterion is optional but at
 * least one must be present (typed errno 402 otherwise); {@code preview} true
 * returns the matched count only, with zero side effects.
 */
public class CouponDeliverRequest {

    /** Paid an order within this many days. */
    private Integer recencyDays;
    /** At least this many lifetime paid orders. */
    private Integer minFrequency;
    /** At least this much lifetime paid spend (plain decimal). */
    private BigDecimal minMonetary;
    private Boolean preview;

    public Integer getRecencyDays() {
        return recencyDays;
    }

    public void setRecencyDays(Integer recencyDays) {
        this.recencyDays = recencyDays;
    }

    public Integer getMinFrequency() {
        return minFrequency;
    }

    public void setMinFrequency(Integer minFrequency) {
        this.minFrequency = minFrequency;
    }

    public BigDecimal getMinMonetary() {
        return minMonetary;
    }

    public void setMinMonetary(BigDecimal minMonetary) {
        this.minMonetary = minMonetary;
    }

    public Boolean getPreview() {
        return preview;
    }

    public void setPreview(Boolean preview) {
        this.preview = preview;
    }
}
