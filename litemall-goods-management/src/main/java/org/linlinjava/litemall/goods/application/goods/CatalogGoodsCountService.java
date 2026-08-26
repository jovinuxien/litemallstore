package org.linlinjava.litemall.goods.application.goods;

import com.github.pagehelper.Page;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * On-sale goods count per ROOT (L1) category over its whole subtree (L2 + L3 descendants).
 * Backs the storefront's "most promising categories first" ordering of {@code GET /srv/catalog/all}.
 *
 * <p>Counts ride PageHelper's {@link Page#getTotal()} on a limit-1 query per root (no litemall-db
 * change), and the whole map is memoized for {@link #TTL_MS} — catalog size only moves on CJ syncs
 * and admin edits, so a 5-minute staleness is invisible while keeping the endpoint cheap.
 */
@Service
public class CatalogGoodsCountService {

    private static final long TTL_MS = 5 * 60 * 1000L;

    private final LitemallCategoryService categoryService;
    private final LitemallGoodsService goodsService;

    private volatile Map<Integer, Long> cache = Map.of();
    private volatile long cachedAt = 0L;

    public CatalogGoodsCountService(LitemallCategoryService categoryService,
                                    LitemallGoodsService goodsService) {
        this.categoryService = categoryService;
        this.goodsService = goodsService;
    }

    /** Root category id → on-sale goods count in its subtree. Memoized ~5 min. */
    public Map<Integer, Long> countsByRoot() {
        Map<Integer, Long> snapshot = cache;
        if (System.currentTimeMillis() - cachedAt < TTL_MS && !snapshot.isEmpty()) {
            return snapshot;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - cachedAt < TTL_MS && !cache.isEmpty()) {
                return cache;
            }
            Map<Integer, Long> fresh = new LinkedHashMap<>();
            for (LitemallCategory root : categoryService.queryL1()) {
                fresh.put(root.getId(), countOnSale(subtreeIds(root.getId())));
            }
            cache = fresh;
            cachedAt = System.currentTimeMillis();
            return fresh;
        }
    }

    /**
     * On-sale goods in one category's subtree, counted live (not memoized).
     *
     * <p>Exposed for the sitemap, which needs per-SUBCATEGORY counts rather than the per-root map
     * above. It shares {@link #countOnSale} with {@link #countsByRoot} deliberately: a sitemap
     * that judged "has products" by a different rule than the navigation could advertise a
     * category page the storefront treats as empty.
     */
    public long countOnSaleInSubtree(Integer categoryId) {
        if (categoryId == null || categoryId <= 0) {
            return 0L;
        }
        return countOnSale(subtreeIds(categoryId));
    }

    /** The root id plus every descendant category id (depth-first walk of {@code queryByPid}). */
    public List<Integer> subtreeIds(Integer rootId) {
        List<Integer> acc = new ArrayList<>();
        collectSubtreeIds(rootId, acc);
        return acc;
    }

    private void collectSubtreeIds(Integer id, List<Integer> acc) {
        acc.add(id);
        for (LitemallCategory child : categoryService.queryByPid(id)) {
            collectSubtreeIds(child.getId(), acc);
        }
    }

    private long countOnSale(List<Integer> categoryIds) {
        List<LitemallGoods> page = goodsService.queryByCategory(categoryIds, 1, 1);
        return page instanceof Page<?> p ? p.getTotal() : page.size();
    }
}
