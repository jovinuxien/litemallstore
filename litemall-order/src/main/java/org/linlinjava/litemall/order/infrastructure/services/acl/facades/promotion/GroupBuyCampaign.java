package org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion;

import java.math.BigDecimal;

/**
 * A combination group-buy campaign definition as promotion reports it
 * ({@code GET /srv/promotion/combination/{combinationId}}). {@code limitPerUser}
 * is read tolerantly — promotion's public DTO may not expose it yet; absent means
 * "no per-user cap".
 */
public class GroupBuyCampaign {

    private final Integer combinationId;
    private final Integer goodsId;
    private final BigDecimal combinationPrice;
    private final Integer limitPerUser;

    public GroupBuyCampaign(Integer combinationId, Integer goodsId,
                            BigDecimal combinationPrice, Integer limitPerUser) {
        this.combinationId = combinationId;
        this.goodsId = goodsId;
        this.combinationPrice = combinationPrice;
        this.limitPerUser = limitPerUser;
    }

    public Integer getCombinationId() {
        return combinationId;
    }

    public Integer getGoodsId() {
        return goodsId;
    }

    /** Group unit price the line is charged at — plain decimal, promotion-authoritative. */
    public BigDecimal getCombinationPrice() {
        return combinationPrice;
    }

    /** Per-user quantity cap, or null when the campaign sets none. */
    public Integer getLimitPerUser() {
        return limitPerUser;
    }
}
