package org.linlinjava.litemall.goods.domain.model.dto.goods;

public class GoodsServiceResponseCode {

    public static final Integer GOODS_NAME_EXIST = 611;

    /**
     * Admin comment reply already exists (admin_content is set-once). Upstream
     * litemall-admin-api name: ORDER_REPLY_EXIST. NOTE: 622, not 620.
     */
    public static final Integer ORDER_REPLY_EXIST = 622;

    // Content subdomain (Wave 4) — see docs/spec-page-palette-v1.md §6.
    // 631/632 are inline stock literals in LitemallGoodsController — don't re-mint.
    /** Palette validation failure; errmsg names the offending component. */
    public static final Integer PAGE_CONFIG_INVALID = 640;
    /** Activate race lost / delete refused on active home / category delete refused while referenced. */
    public static final Integer CONTENT_CONFLICT = 641;
    /** No active home page / requested page not active. */
    public static final Integer PAGE_NOT_ACTIVE = 642;
    /** Article missing or hidden on the customer read path. */
    public static final Integer ARTICLE_NOT_AVAILABLE = 643;
    /** Flash-deal validation failure; errmsg names the reason (price, window, goods…). */
    public static final Integer DEAL_INVALID = 650;
    /** An enabled deal already overlaps this goods+window, or the deal is live and immutable. */
    public static final Integer DEAL_CONFLICT = 651;
    /** Flash deals refuse source='cj' goods (CJ price sync would fight the price swap).
     *  A cost-floor + sync-guard design is PARKED pending an efficiency redesign — see
     *  doc/cj-deals-strategy-2026-07-16.pdf and commit a82a19e0e. */
    public static final Integer DEAL_CJ_UNSUPPORTED = 652;

}
