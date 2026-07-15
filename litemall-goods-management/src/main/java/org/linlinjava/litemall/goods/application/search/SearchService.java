package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.goods.application.comment.CommentStatsService;
import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsSearchClient;
import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsSearchResult;
import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsSuggestClient;
import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsSuggestion;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Application-layer facade over the OCS search + suggest adapters.
 * Controllers depend on this; they never touch the {@code infrastructure/acl/ocs}
 * classes directly so the boundary set up by package-info is honoured at the
 * call-site too.
 */
@Service
public class SearchService {

    private final OcsSearchClient searchClient;
    private final OcsSuggestClient suggestClient;
    private final CommentStatsService commentStatsService;

    public SearchService(OcsSearchClient searchClient, OcsSuggestClient suggestClient,
                         CommentStatsService commentStatsService) {
        this.searchClient = searchClient;
        this.suggestClient = suggestClient;
        this.commentStatsService = commentStatsService;
    }

    public Map<String, Object> search(String query, int page, int size, String sort, Map<String, String> filters) {
        // A non-positive page size (e.g. an InstantSearch facet-probe sending hitsPerPage=0) would
        // become limit=0 on the OCS URL and return zero hits — clamp to a sane default instead.
        if (size <= 0) {
            size = 20;
        }
        int offset = Math.max(0, (page - 1) * size);
        // Forward all candidate filters; OCS only acts on params matching a configured Facet field and
        // ignores the rest (verified), so the "whitelist" is the index's own facet set, not a hardcoded
        // list — new facets (attributes, variant fields) need no Java change.
        OcsSearchResult result = searchClient.search(query, offset, size, sort, filters);

        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> facets = new ArrayList<>();
        Set<String> facetFields = new HashSet<>();
        long total = 0L;
        if (result != null && result.getSlices() != null) {
            total = result.totalMatchCount();
            for (OcsSearchResult.Slice slice : result.getSlices()) {
                if (slice.getHits() != null) {
                    for (OcsSearchResult.Hit hit : slice.getHits()) {
                        items.add(toGoodsListItem(hit));
                    }
                }
                if (slice.getFacets() != null) {
                    for (OcsSearchResult.Facet facet : slice.getFacets()) {
                        facets.add(toFacetView(facet));
                        facetFields.add(facet.getFieldName());
                    }
                }
            }
        }
        // Batch-decorate the page's local hits with review stats (star avg + count); cj_ ids skipped.
        commentStatsService.decorate(items);
        Map<String, Object> response = new HashMap<>();
        response.put("totalPages", computeTotalPages(total, size));
        response.put("total", total);
        response.put("page", page);
        response.put("limit", size);
        response.put("goodsList", items);
        response.put("filters", facets);
        response.put("sortOptions", toSortOptionViews(result));
        response.put("appliedFilters", appliedFilters(filters, facetFields));
        response.put("queryStrategy", queryStrategy(result));
        response.put("relaxed", isRelaxed(result));
        return response;
    }

    public List<String> suggest(String query) {
        List<OcsSuggestion> suggestions = suggestClient.suggest(query);
        List<String> phrases = new ArrayList<>(suggestions.size());
        for (OcsSuggestion s : suggestions) {
            if (s.getPhrase() != null) {
                phrases.add(s.getPhrase());
            }
        }
        return phrases;
    }

    private Map<String, Object> toGoodsListItem(OcsSearchResult.Hit hit) {
        Map<String, Object> item = new HashMap<>();
        OcsSearchResult.Document document = hit.getDocument();
        if (document == null) {
            return item;
        }
        item.put("id", document.getId());
        Map<String, Object> data = document.getData();
        if (data != null) {
            item.put("name", data.get("title"));
            item.put("brief", data.get("description"));
            item.put("picUrl", data.get("image_url"));
            item.put("retailPrice", data.getOrDefault("discount_price", data.get("price")));
            item.put("counterPrice", data.get("price"));
            item.put("brand", data.get("brand"));
            item.put("categoryNames", data.get("category_names"));
            // Origin tag ("local" | "cj_dropshipping"). Metadata/routing only — the result set
            // is ONE unified ranked list; CJ hits are identified by their cj_<pid> id. This never
            // splits or default-filters results.
            item.put("source", data.get("source"));
            // Index-time markdown percent — the SPA renders it as the "X% off" card badge
            // (badges are data, not client-side price math, so badge and ranking always agree).
            Object discountPct = data.get("discount_pct");
            if (discountPct instanceof Number && ((Number) discountPct).intValue() > 0) {
                item.put("discountPct", ((Number) discountPct).intValue());
            }
        }
        return item;
    }

    /** The subset of requested filters OCS actually acted on — i.e. those matching a returned facet. */
    private Map<String, String> appliedFilters(Map<String, String> filters, Set<String> facetFields) {
        Map<String, String> applied = new LinkedHashMap<>();
        if (filters != null) {
            for (Map.Entry<String, String> entry : filters.entrySet()) {
                if (facetFields.contains(entry.getKey())
                        && entry.getValue() != null && !entry.getValue().isBlank()) {
                    applied.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return applied;
    }

    private Map<String, Object> toFacetView(OcsSearchResult.Facet facet) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("field", facet.getFieldName());
        view.put("type", facet.getType());
        List<Map<String, Object>> entries = new ArrayList<>();
        if (facet.getEntries() != null) {
            for (OcsSearchResult.FacetEntry entry : facet.getEntries()) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("value", entry.getKey());
                e.put("id", entry.getId());
                e.put("count", entry.getDocCount());
                e.put("selected", entry.isSelected());
                entries.add(e);
            }
        }
        view.put("entries", entries);
        return view;
    }

    private List<Map<String, Object>> toSortOptionViews(OcsSearchResult result) {
        List<Map<String, Object>> options = new ArrayList<>();
        if (result == null || result.getSortOptions() == null) {
            return options;
        }
        for (OcsSearchResult.SortOption option : result.getSortOptions()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("label", option.getLabel());
            // The value a caller sends back as `sort`: `field` for ASC, `-field` for DESC
            // (the `-` syntax is what the live searcher honours; `.desc` is not).
            boolean desc = "DESC".equalsIgnoreCase(option.getSortOrder());
            view.put("value", desc ? "-" + option.getField() : option.getField());
            view.put("active", option.isActive());
            options.add(view);
        }
        return options;
    }

    private String queryStrategy(OcsSearchResult result) {
        if (result == null || result.getMeta() == null) {
            return null;
        }
        Object strategy = result.getMeta().get("query_strategy");
        return strategy == null ? null : strategy.toString();
    }

    /**
     * True when OCS had to relax the query to find hits (typo/fuzzy/ngram fallback) rather than
     * matching the terms exactly. Driven by the searcher's {@code meta.query_stage}: stage 0 is the
     * primary (exact) strategy; any higher stage means a relaxation strategy produced the results.
     * Lets the customer SPA show a "showing results for a broadened search" hint instead of a bare
     * result page (and distinguish a relaxed hit-set from an exact one).
     */
    private boolean isRelaxed(OcsSearchResult result) {
        if (result == null || result.getMeta() == null) {
            return false;
        }
        Object stage = result.getMeta().get("query_stage");
        return stage instanceof Number && ((Number) stage).intValue() > 0;
    }

    private int computeTotalPages(long total, int size) {
        if (size <= 0) {
            return 0;
        }
        return (int) ((total + size - 1) / size);
    }
}
