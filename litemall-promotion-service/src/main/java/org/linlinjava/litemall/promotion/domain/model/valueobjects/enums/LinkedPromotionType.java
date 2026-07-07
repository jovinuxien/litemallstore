package org.linlinjava.litemall.promotion.domain.model.valueobjects.enums;

/**
 * The Phase-1 promotion mechanic a campaign drives its targeted audience toward.
 * A campaign reuses an existing coupon/seckill/bargain/combination definition by
 * id rather than duplicating mechanics. {@link #NONE} means the campaign only
 * produces an audience assignment (e.g. for downstream delivery) without a
 * pre-bound mechanic.
 */
public enum LinkedPromotionType {
    COUPON,
    SECKILL,
    BARGAIN,
    COMBINATION,
    NONE;

    public static LinkedPromotionType fromName(String name) {
        if (name == null || name.isBlank()) {
            return NONE;
        }
        return LinkedPromotionType.valueOf(name.trim().toUpperCase());
    }
}
