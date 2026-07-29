package org.linlinjava.litemall.promotion.application.ports;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Inbound port for the catalog facts the social vertical needs: goods facts +
 * media candidates for the composer, and the live (price-swapped) flash deals
 * that drive the opt-in auto-poster. Implemented in {@code infrastructure}
 * over the shared litemall-db read model plus the goods-service video endpoint
 * (Wave-3 {@code GET /srv/goods/videos}) — application code never touches a
 * mapper or Feign client directly.
 */
public interface SocialCatalogPort {

    /** Goods facts + media candidates; empty when the goods does not exist (or is deleted). */
    Optional<GoodsSocialSnapshot> goodsSnapshot(Integer goodsId);

    /** The currently live (price-swapped) deal on a goods, if any. */
    Optional<LiveDeal> liveDealFor(Integer goodsId);

    /** All currently live (price-swapped, enabled, in-window) flash deals. */
    List<LiveDeal> liveDeals();

    /**
     * A category root and the live deals on goods in its tree (the category
     * itself plus its direct children) — the target set of a Wave-12
     * from-category campaign. Empty when the category does not exist.
     */
    Optional<CategoryLiveDeals> categoryLiveDeals(Integer categoryId);

    /**
     * @param videoUrl first live product video, or null — a null gates TikTok
     *                 (video-only platform); resolution is fail-soft (goods-service
     *                 down ⇒ null, never an exception)
     */
    record GoodsSocialSnapshot(Integer goodsId, String name, BigDecimal retailPrice,
                               BigDecimal counterPrice, String picUrl,
                               List<String> gallery, String videoUrl) {
    }

    /** @param originalPrice the pre-swap retail captured at activation (may be null on legacy rows) */
    record LiveDeal(Integer dealId, Integer goodsId, BigDecimal dealPrice,
                    BigDecimal originalPrice, LocalDateTime startTime, LocalDateTime stopTime) {
    }

    /** @param deals live deals on goods under this category; empty list = none right now */
    record CategoryLiveDeals(Integer categoryId, String categoryName, List<LiveDeal> deals) {
    }
}
