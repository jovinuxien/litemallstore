package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exact category name → id resolution for typed suggest entries (the {@code EngagementGoodsResolver}
 * precedent: a small stateless resolver returning an id or null, never throwing). Backed by a
 * periodically refreshed in-memory snapshot of every live category (the table is small — the CJ
 * tree mirror plus natives), matched case-insensitively on the trimmed name.
 *
 * <p>Duplicate names across the tree (a known live condition — see the sibling-name-collision
 * note in {@code CategorySearchService}) resolve to {@code null}: deep-linking a user to an
 * arbitrary one of two same-named categories is worse than degrading the suggestion to a plain
 * keyword search.
 */
@Component
public class CategoryNameResolver {

    private static final Logger log = LoggerFactory.getLogger(CategoryNameResolver.class);

    private static final long CACHE_TTL_MILLIS = 60_000L;
    /** querySelective pages unconditionally; one oversized page = the whole (small) table. */
    private static final int FETCH_LIMIT = 10_000;

    private final LitemallCategoryService categoryService;

    private volatile Snapshot snapshot;

    public CategoryNameResolver(LitemallCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** The single live category with this exact name (case-insensitive), or null (unknown/ambiguous). */
    public Integer resolveId(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Map<String, Integer> idsByName = currentIdsByName();
        return idsByName == null ? null : idsByName.get(normalize(name));
    }

    private Map<String, Integer> currentIdsByName() {
        Snapshot current = snapshot;
        long now = System.currentTimeMillis();
        if (current != null && now - current.loadedAt < CACHE_TTL_MILLIS) {
            return current.idsByName;
        }
        try {
            Map<String, Integer> fresh = load();
            snapshot = new Snapshot(now, fresh);
            return fresh;
        } catch (Exception e) {
            // Fail-soft: suggestions degrade to keywords; a stale snapshot beats none at all.
            log.warn("category snapshot refresh failed — {}", current != null
                    ? "serving stale name->id map" : "category suggestions degrade to keywords", e);
            return current != null ? current.idsByName : null;
        }
    }

    private Map<String, Integer> load() {
        List<LitemallCategory> categories =
                categoryService.querySelective(null, null, 1, FETCH_LIMIT, null, null);
        Map<String, Integer> idsByName = new HashMap<>();
        for (LitemallCategory category : categories) {
            if (category.getName() == null || category.getId() == null) {
                continue;
            }
            String key = normalize(category.getName());
            if (key.isEmpty()) {
                continue;
            }
            Integer previous = idsByName.putIfAbsent(key, category.getId());
            if (previous != null && !previous.equals(category.getId())) {
                // Ambiguous name — poison the entry so lookups return null.
                idsByName.put(key, null);
            }
        }
        return idsByName;
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Snapshot {
        final long loadedAt;
        final Map<String, Integer> idsByName;

        Snapshot(long loadedAt, Map<String, Integer> idsByName) {
            this.loadedAt = loadedAt;
            this.idsByName = idsByName;
        }
    }
}
