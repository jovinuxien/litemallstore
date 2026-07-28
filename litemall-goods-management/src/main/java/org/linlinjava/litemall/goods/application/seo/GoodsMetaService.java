package org.linlinjava.litemall.goods.application.seo;

import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slim product meta for the Wave-13 head-injection contract ({@code GET /srv/goods/meta/{id}}):
 * local tables only, TTL-cached, and {@code updateTime} pre-formatted as an ISO-8601 STRING —
 * the module's ObjectMapper (litemall-core JacksonConfig's raw bean) writes {@code LocalDateTime}
 * as arrays, which the contract forbids for this field.
 *
 * <p>Off-sale goods ARE served (PDPs stay viewable-unbuyable and crawlers may still visit);
 * only missing/soft-deleted goods yield empty. {@code rating} stays {@code null} when never
 * rated — never a fake 0.
 */
@Service
public class GoodsMetaService {

    private static final long TTL_MS = 5 * 60 * 1000L;
    /** Crude bound so crawler id-sweeps can't grow the cache without limit (~9.6k real goods). */
    private static final int MAX_CACHE_ENTRIES = 20_000;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final LitemallGoodsService goodsService;
    private final LitemallCategoryService categoryService;
    private final LitemallGoodsProperties goodsProperties;

    private record CachedMeta(Map<String, Object> meta, long cachedAt) {
    }

    private final ConcurrentHashMap<Integer, CachedMeta> cache = new ConcurrentHashMap<>();

    public GoodsMetaService(LitemallGoodsService goodsService,
                            LitemallCategoryService categoryService,
                            LitemallGoodsProperties goodsProperties) {
        this.goodsService = goodsService;
        this.categoryService = categoryService;
        this.goodsProperties = goodsProperties;
    }

    /** Contract meta for a live (non-deleted) goods row; empty when missing/soft-deleted. */
    public Optional<Map<String, Object>> meta(int goodsId) {
        long now = System.currentTimeMillis();
        CachedMeta hit = cache.get(goodsId);
        if (hit != null && now - hit.cachedAt() < TTL_MS) {
            return Optional.of(hit.meta());
        }
        LitemallGoods goods = goodsService.findById(goodsId); // binds deleted=false
        if (goods == null) {
            cache.remove(goodsId);
            return Optional.empty();
        }
        Map<String, Object> meta = build(goods);
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(goodsId, new CachedMeta(meta, now));
        return Optional.of(meta);
    }

    private Map<String, Object> build(LitemallGoods goods) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", goods.getId());
        meta.put("name", goods.getName());
        meta.put("brief", goods.getBrief());
        meta.put("picUrl", goods.getPicUrl());
        meta.put("retailPrice", goods.getRetailPrice());
        meta.put("currency", goodsProperties.getCurrency());
        meta.put("onSale", Boolean.TRUE.equals(goods.getIsOnSale()));
        meta.put("rating", goods.getRating());
        meta.put("reviewCount", goods.getReviewCount() != null ? goods.getReviewCount() : 0);
        meta.put("categoryId", goods.getCategoryId());
        meta.put("categoryName", categoryName(goods.getCategoryId()));
        meta.put("updateTime", goods.getUpdateTime() != null ? ISO.format(goods.getUpdateTime()) : null);
        return Collections.unmodifiableMap(meta);
    }

    private String categoryName(Integer categoryId) {
        if (categoryId == null || categoryId <= 0) {
            return null;
        }
        LitemallCategory category = categoryService.findById(categoryId);
        return category != null && !Boolean.TRUE.equals(category.getDeleted()) ? category.getName() : null;
    }
}
