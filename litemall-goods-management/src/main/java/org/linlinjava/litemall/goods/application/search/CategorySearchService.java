package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.infrastructure.services.api.LitemallCatalogService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Category-scoped faceted search: the backing for the "click a category &rarr; land on the
 * search page already filtered to that category" flow.
 *
 * <p>It is pure composition over two existing services and adds no new OCS or persistence
 * access of its own:
 * <ul>
 *   <li>{@link SearchService#search} runs the OCS query with {@code category_ids=<id>} added to
 *       the filter set. Because OCS does drill-down faceting, every returned facet
 *       (brand, price, variant_price, source, curated attributes) is already scoped to the
 *       selected category and self-hides when the category has no docs for it.</li>
 *   <li>{@link LitemallCatalogService} supplies the names/hierarchy that OCS does not: the
 *       breadcrumb and the child subcategories.</li>
 * </ul>
 *
 * <p><b>Subcategory counts.</b> When the query filters on {@code category_ids}, OCS collapses the
 * {@code category_ids} facet to the selected id alone (the filtered field reflects the filter, not
 * the distribution). The child breakdown instead surfaces in the <em>unfiltered</em>
 * {@code category_names} facet (verified live): each child name maps to its in-category doc count.
 * Because the index stores the full ancestor chain, a child's count there is already constrained
 * to this category. We therefore scope by id (exact result set) but read child counts by name.
 * Caveat: two sibling categories sharing a name (a data anomaly &mdash; only the "underwear" pair
 * today) would receive the same summed count; children with no docs are absent from the facet and
 * default to 0.
 */
@Service
public class CategorySearchService {

    /** Flat category facets are replaced by {@code breadcrumb} + {@code subcategories}, so drop them from the rail. */
    private static final Set<String> CATEGORY_FACET_FIELDS = Set.of("category_ids", "category_names");

    /** Field used to scope the result set exactly (by id). */
    private static final String CATEGORY_FILTER_FIELD = "category_ids";

    /** Field whose facet carries the per-child breakdown when scoping by id (keyed by name). */
    private static final String CHILD_BREAKDOWN_FIELD = "category_names";

    private final SearchService searchService;
    private final LitemallCatalogService catalogService;

    public CategorySearchService(SearchService searchService, LitemallCatalogService catalogService) {
        this.searchService = searchService;
        this.catalogService = catalogService;
    }

    /**
     * @return the composed category-landing payload, or {@code null} when the category does not exist
     *         (the controller maps that to a 404).
     */
    public Map<String, Object> searchByCategory(Integer categoryId, String query, int page, int size,
                                                String sort, Map<String, String> extraFilters) {
        LitemallCategoryAggregate category = catalogService.getCategoryById(new LitemallCategoryId(categoryId));
        if (category == null) {
            return null;
        }

        Map<String, String> filters = extraFilters == null ? new HashMap<>() : new HashMap<>(extraFilters);
        filters.put(CATEGORY_FILTER_FIELD, String.valueOf(categoryId));

        Map<String, Object> result = searchService.search(query, page, size, sort, filters);

        Map<String, Long> childCounts = extractChildCountsByName(result);
        stripCategoryFacets(result);

        result.put("category", toCategoryView(category));
        result.put("breadcrumb", buildBreadcrumb(category));
        result.put("subcategories", buildSubcategories(categoryId, childCounts));
        return result;
    }

    /**
     * Root&rarr;leaf breadcrumb. NOTE: {@link LitemallCategoryAggregate#getParentId()} is unreliable —
     * the repository maps it to the category's <em>own</em> id (see
     * {@code LitemallCatalogRepositoryImpl#convertToDomainModel}), so it cannot be walked. The
     * taxonomy is two-level (L1 &rarr; L2), so an L2's parent is resolved by finding the L1 whose
     * children include it. (The buggy mapping is left untouched here; fixing it ripples into the
     * catalog controller + write path and is a separate follow-up.)
     */
    private List<Map<String, Object>> buildBreadcrumb(LitemallCategoryAggregate category) {
        List<Map<String, Object>> crumbs = new ArrayList<>();
        if (!"L1".equalsIgnoreCase(category.getLevel())) {
            LitemallCategoryAggregate parent = findParent(category);
            if (parent != null) {
                crumbs.add(toCategoryView(parent));
            }
        }
        crumbs.add(toCategoryView(category));
        return crumbs;
    }

    /** Finds the L1 whose direct children include {@code child}; null if none (data anomaly). */
    private LitemallCategoryAggregate findParent(LitemallCategoryAggregate child) {
        Integer childId = child.getCategoryId().getId();
        for (LitemallCategoryAggregate l1 : catalogService.getFirstLevelCategories()) {
            for (LitemallCategoryAggregate sub : catalogService.queryByPid(l1.getCategoryId().getId())) {
                if (childId.equals(sub.getCategoryId().getId())) {
                    return l1;
                }
            }
        }
        return null;
    }

    /** Direct children of the category, each annotated with its in-category doc count (matched by name). */
    private List<Map<String, Object>> buildSubcategories(Integer categoryId, Map<String, Long> childCounts) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (LitemallCategoryAggregate child : catalogService.queryByPid(categoryId)) {
            Map<String, Object> node = toCategoryView(child);
            node.put("count", childCounts.getOrDefault(child.getCategoryName(), 0L));
            // The taxonomy is two-level (L1 -> L2 leaf); an L2 child has no further children.
            node.put("hasChildren", "L1".equalsIgnoreCase(child.getLevel()));
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * Pulls name&rarr;count from the {@code category_names} facet of a {@link SearchService#search}
     * result — the field that carries the child breakdown when the query is scoped by id.
     */
    private Map<String, Long> extractChildCountsByName(Map<String, Object> result) {
        Map<String, Long> counts = new HashMap<>();
        Object filters = result.get("filters");
        if (!(filters instanceof List<?> facetList)) {
            return counts;
        }
        for (Object facetObj : facetList) {
            if (!(facetObj instanceof Map<?, ?> facet)) {
                continue;
            }
            if (!CHILD_BREAKDOWN_FIELD.equals(facet.get("field"))) {
                continue;
            }
            Object entries = facet.get("entries");
            if (entries instanceof List<?> entryList) {
                for (Object entryObj : entryList) {
                    if (entryObj instanceof Map<?, ?> entry) {
                        Object value = entry.get("value");
                        Object count = entry.get("count");
                        if (value != null && count instanceof Number number) {
                            counts.put(String.valueOf(value), number.longValue());
                        }
                    }
                }
            }
        }
        return counts;
    }

    /** Removes the flat category facets from the rail; they are now surfaced as breadcrumb + subcategories. */
    private void stripCategoryFacets(Map<String, Object> result) {
        Object filters = result.get("filters");
        if (filters instanceof List<?> facetList) {
            facetList.removeIf(facet -> facet instanceof Map<?, ?> map
                    && CATEGORY_FACET_FIELDS.contains(map.get("field")));
        }
    }

    private Map<String, Object> toCategoryView(LitemallCategoryAggregate category) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", category.getCategoryId().getId());
        view.put("name", category.getCategoryName());
        view.put("level", category.getLevel());
        view.put("iconUrl", category.getIconUrl());
        view.put("picUrl", category.getPicUrl());
        return view;
    }
}
