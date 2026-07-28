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
    /** CJ deal has NO usable cost basis (cost never captured) — deal refused. Narrowed in
     *  Wave 12 from the old blanket "CJ goods refused": CJ deals are LIVE again, floored at
     *  the real captured cost ({@code litemall_goods.cost}); 650 covers a price below that
     *  floor. Ref: doc/cj-deals-strategy-2026-07-16.pdf, commits a82a19e0e / Wave 12. */
    public static final Integer DEAL_CJ_UNSUPPORTED = 652;

    // Insight subdomain (Wave 12).
    /** Deal-candidate action refused: no candidate for the goods, or it is already decided. */
    public static final Integer INSIGHT_CANDIDATE_INVALID = 653;

    // SEO subdomain (Wave 13).
    /** /srv/goods/meta/{id}: goods missing or soft-deleted (off-sale is still served). */
    public static final Integer GOODS_NOT_FOUND = 654;

}
