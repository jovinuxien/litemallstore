package org.linlinjava.litemall.goods.application.promo;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.springframework.stereotype.Component;

/**
 * Leaf → L1 root resolution over {@code litemall_category} ({@code pid} 0 = root),
 * memoized ~5 min (the {@code CatalogGoodsCountService} TTL convention — CJ syncs can
 * add leaves nightly, admins rarely re-parent). Unknown/broken chains resolve to null.
 */
@Component
public class CategoryRootResolver {

    private static final long TTL_MS = 5 * 60 * 1000L;
    private static final int MAX_HOPS = 10;

    private final LitemallCategoryService categoryService;
    private final Map<Integer, Optional<Integer>> memo = new ConcurrentHashMap<>();
    private volatile long builtAt = 0L;

    public CategoryRootResolver(LitemallCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    public Integer rootOf(Integer categoryId) {
        if (categoryId == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now - builtAt > TTL_MS) {
            memo.clear();
            builtAt = now;
        }
        return memo.computeIfAbsent(categoryId, this::walk).orElse(null);
    }

    private Optional<Integer> walk(Integer categoryId) {
        Integer current = categoryId;
        for (int hops = 0; hops < MAX_HOPS && current != null; hops++) {
            LitemallCategory category = categoryService.findById(current);
            if (category == null) {
                return Optional.empty();
            }
            if (category.getPid() == null || category.getPid() == 0) {
                return Optional.of(category.getId());
            }
            current = category.getPid();
        }
        return Optional.empty();
    }
}
