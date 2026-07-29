package org.linlinjava.litemall.goods.application.pricing;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.linlinjava.litemall.db.dao.LitemallCategoryMarginMapper;
import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallCategoryMargin;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Effective CJ pricing margin per category (Wave 14): the L1-root override from
 * {@code litemall_category_margin} when one exists, else the global
 * {@code spring.cjdropship.pricing.margin}.
 *
 * <p>The reprice sites hold different category handles — the sync/enrichment know only the raw
 * CJ leaf UUID, the promote/insight paths know the local {@code litemall_goods.category_id} —
 * so both directions resolve here: CJ leaf UUID → mirrored local category (via
 * {@code litemall_cj_category} linkage) → L1 root (inverted subtree walk). Every failure is
 * fail-open to the global margin: a broken lookup must never block pricing, and a missing
 * override is simply "use the default".
 *
 * <p>Caches: overrides ~60 s (and invalidated by the admin CRUD so a PUT applies immediately),
 * the category→root map ~5 min (the tree changes only via the nightly mirror), CJ-leaf links
 * alongside it.
 */
@Component
public class CategoryMarginResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(CategoryMarginResolver.class);

    private static final long OVERRIDES_TTL_MS = 60 * 1000L;
    private static final long TREE_TTL_MS = 5 * 60 * 1000L;

    private final LitemallCategoryMarginMapper marginMapper;
    private final LitemallCjLinkageMapper linkageMapper;
    private final LitemallCategoryService categoryService;
    private final CJDropshippingConfig config;

    private volatile Map<Integer, BigDecimal> overrides = Map.of();
    private volatile long overridesAt = 0L;

    private volatile Map<Integer, Integer> categoryToRoot = Map.of();
    private volatile long treeAt = 0L;

    /** CJ leaf UUID → mirrored local category id; misses cached too (empty Optional). */
    private final ConcurrentHashMap<String, Optional<Integer>> cjLeafToCategory = new ConcurrentHashMap<>();

    public CategoryMarginResolver(LitemallCategoryMarginMapper marginMapper,
                                  LitemallCjLinkageMapper linkageMapper,
                                  LitemallCategoryService categoryService,
                                  CJDropshippingConfig config) {
        this.marginMapper = marginMapper;
        this.linkageMapper = linkageMapper;
        this.categoryService = categoryService;
        this.config = config;
    }

    /** The global default margin ({@code spring.cjdropship.pricing.margin}). */
    public BigDecimal globalMargin() {
        return config.getPricing().getMargin();
    }

    /** Effective margin for an L1 root id: its override, else global. Null-safe. */
    public BigDecimal effectiveForRoot(Integer rootId) {
        if (rootId == null) {
            return globalMargin();
        }
        return overridesSnapshot().getOrDefault(rootId, globalMargin());
    }

    /** Effective margin for any local category id (leaf or root): resolved to its L1 root. */
    public BigDecimal effectiveForCategory(Integer categoryId) {
        return effectiveForRoot(rootOfCategory(categoryId));
    }

    /** Effective margin for a raw CJ leaf category UUID (sync/enrichment paths). */
    public BigDecimal effectiveForCjLeaf(String cjLeafUuid) {
        if (cjLeafUuid == null || cjLeafUuid.isBlank() || overridesSnapshot().isEmpty()) {
            return globalMargin();
        }
        try {
            Optional<Integer> local = cjLeafToCategory.computeIfAbsent(cjLeafUuid,
                    uuid -> Optional.ofNullable(linkageMapper.findCjCategoryIdByCjId(uuid)));
            return local.map(this::effectiveForCategory).orElseGet(this::globalMargin);
        } catch (RuntimeException e) {
            LOGGER.warn("CJ leaf {} margin resolution failed — global margin applies: {}",
                    cjLeafUuid, e.getMessage());
            return globalMargin();
        }
    }

    /** L1 root of a local category id, or null when the category is unknown/orphaned. */
    public Integer rootOfCategory(Integer categoryId) {
        if (categoryId == null) {
            return null;
        }
        return treeSnapshot().get(categoryId);
    }

    /** Called by the admin margin CRUD so a PUT/DELETE takes effect immediately. */
    public void invalidateOverrides() {
        overridesAt = 0L;
    }

    private Map<Integer, BigDecimal> overridesSnapshot() {
        Map<Integer, BigDecimal> snapshot = overrides;
        long now = System.currentTimeMillis();
        if (now - overridesAt < OVERRIDES_TTL_MS) {
            return snapshot;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - overridesAt < OVERRIDES_TTL_MS) {
                return overrides;
            }
            try {
                Map<Integer, BigDecimal> fresh = new HashMap<>();
                for (LitemallCategoryMargin m : marginMapper.selectAll()) {
                    fresh.put(m.getCategoryId(), m.getMargin());
                }
                overrides = Map.copyOf(fresh);
            } catch (RuntimeException e) {
                LOGGER.warn("category margin overrides unreadable — keeping previous snapshot: {}",
                        e.getMessage());
            }
            overridesAt = System.currentTimeMillis();
            return overrides;
        }
    }

    private Map<Integer, Integer> treeSnapshot() {
        Map<Integer, Integer> snapshot = categoryToRoot;
        long now = System.currentTimeMillis();
        if (now - treeAt < TREE_TTL_MS && !snapshot.isEmpty()) {
            return snapshot;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - treeAt < TREE_TTL_MS && !categoryToRoot.isEmpty()) {
                return categoryToRoot;
            }
            try {
                Map<Integer, Integer> fresh = new HashMap<>();
                for (LitemallCategory root : categoryService.queryL1()) {
                    collect(root.getId(), root.getId(), fresh);
                }
                categoryToRoot = Map.copyOf(fresh);
                cjLeafToCategory.clear(); // linkage may have re-mirrored alongside the tree
                treeAt = System.currentTimeMillis();
            } catch (RuntimeException e) {
                LOGGER.warn("category tree walk failed — keeping previous root map: {}", e.getMessage());
                treeAt = System.currentTimeMillis();
            }
            return categoryToRoot;
        }
    }

    private void collect(Integer id, Integer rootId, Map<Integer, Integer> acc) {
        acc.put(id, rootId);
        for (LitemallCategory child : categoryService.queryByPid(id)) {
            collect(child.getId(), rootId, acc);
        }
    }
}
