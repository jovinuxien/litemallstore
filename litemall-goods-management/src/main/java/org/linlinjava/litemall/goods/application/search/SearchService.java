package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.goods.application.comment.CommentStatsService;
import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsSearchResult;
import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsSuggestClient;
import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsSuggestion;
import org.springframework.stereotype.Service;

import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    /** Wave-9 contract: OCS suggest tags each entry with its harvest source field. */
    private static final String SUGGEST_SOURCE_CATEGORY = "category_names";
    /** How many curated-keyword matches join the suggest response (after dedupe). */
    private static final int CURATED_SUGGEST_LIMIT = 3;

    private final OcsSearchClient searchClient;
    private final OcsSuggestClient suggestClient;
    private final CommentStatsService commentStatsService;
    private final SearchHighlighter searchHighlighter;
    private final CategoryNameResolver categoryNameResolver;
    private final SearchKeywordService searchKeywordService;

    public SearchService(OcsSearchClient searchClient, OcsSuggestClient suggestClient,
                         CommentStatsService commentStatsService,
                         SearchHighlighter searchHighlighter,
                         CategoryNameResolver categoryNameResolver,
                         SearchKeywordService searchKeywordService) {
        this.searchClient = searchClient;
        this.suggestClient = suggestClient;
        this.commentStatsService = commentStatsService;
        this.searchHighlighter = searchHighlighter;
        this.categoryNameResolver = categoryNameResolver;
        this.searchKeywordService = searchKeywordService;
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
        List<OcsSearchResult.Hit> flatHits = new ArrayList<>();
        List<Map<String, Object>> facets = new ArrayList<>();
        Set<String> facetFields = new HashSet<>();
        long total = 0L;
        if (result != null && result.getSlices() != null) {
            total = result.totalMatchCount();
            for (OcsSearchResult.Slice slice : result.getSlices()) {
                if (slice.getHits() != null) {
                    for (OcsSearchResult.Hit hit : slice.getHits()) {
                        items.add(toGoodsListItem(hit));
                        flatHits.add(hit);
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
        // Wave-9 contract: optional per-hit `highlight` {field: <em>-wrapped snippet}. Strictly
        // decorative — any failure leaves the hits plain rather than failing the search.
        try {
            attachHighlights(query, flatHits, items);
        } catch (Exception e) {
            log.warn("highlight decoration failed — serving plain hits for q='{}'", query, e);
        }
        // Zero-results visibility (RUNBOOK §23): the cheapest relevance-feedback signal there is.
        // Grep for "zero-results search" to harvest synonym/typo/catalog gaps into querqy rules
        // and the judgment list. Only real user queries are worth logging — empty-q browses with
        // over-narrow filters are not a relevance failure.
        if (total == 0 && query != null && !query.isBlank()) {
            log.warn("zero-results search: q='{}' filters={}", query, filters);
        }
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

    /**
     * Typed autocomplete entries per the Wave-9 contract: {@code {text, type, categoryId?}} with
     * {@code type} one of {@code keyword}/{@code category}/{@code curated} and {@code categoryId}
     * present only on {@code category} entries. OCS tags each suggestion with its harvest source
     * field ({@code title}/{@code brand}/{@code category_names} — live-probed 2026-07-24), so
     * category classification is source-driven; the name must additionally resolve to exactly one
     * live category or the entry degrades to a plain keyword (no deep-link is better than a wrong
     * one). Curated keywords (the {@code /helper} table) merge into the SAME response so the SPA
     * has one autocomplete source; OCS being down degrades to curated-only, never an error.
     */
    public List<Map<String, Object>> suggest(String query) {
        List<Map<String, Object>> entries = new ArrayList<>();
        Set<String> seenTexts = new HashSet<>();
        for (OcsSuggestion s : ocsSuggestions(query)) {
            String phrase = s.getPhrase();
            if (phrase == null || phrase.isBlank() || !seenTexts.add(phrase.trim().toLowerCase(Locale.ROOT))) {
                continue;
            }
            entries.add(toSuggestEntry(phrase, s.getType()));
        }
        for (String curated : curatedKeywords(query)) {
            if (curated == null || curated.isBlank() || !seenTexts.add(curated.trim().toLowerCase(Locale.ROOT))) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("text", curated);
            entry.put("type", "curated");
            entries.add(entry);
        }
        return entries;
    }

    private List<OcsSuggestion> ocsSuggestions(String query) {
        try {
            return suggestClient.suggest(query);
        } catch (RestClientException e) {
            log.warn("OCS suggest unavailable — serving curated-only suggestions for q='{}': {}",
                    query, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> curatedKeywords(String query) {
        try {
            return searchKeywordService.helper(query, 1, CURATED_SUGGEST_LIMIT);
        } catch (Exception e) {
            log.warn("curated keyword lookup failed for q='{}'", query, e);
            return Collections.emptyList();
        }
    }

    private Map<String, Object> toSuggestEntry(String phrase, String sourceField) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("text", phrase);
        // An untagged entry (older suggest build) still gets a resolution attempt — the lookup is
        // an in-memory snapshot hit, and an exact category-name match is a strong category signal.
        if (sourceField == null || SUGGEST_SOURCE_CATEGORY.equals(sourceField)) {
            Integer categoryId = categoryNameResolver.resolveId(phrase);
            if (categoryId != null) {
                entry.put("type", "category");
                entry.put("categoryId", categoryId);
                return entry;
            }
        }
        entry.put("type", "keyword");
        return entry;
    }

    /**
     * Attaches the contract's optional {@code highlight} map to each hit item. An OCS-provided
     * per-hit highlight is preferred (normalized so only {@code <em>} markup survives and OCS
     * field names map to our item fields); the deployed searcher returns none (live-probed
     * 2026-07-24), so {@link SearchHighlighter} produces the snippets app-side.
     */
    private void attachHighlights(String query, List<OcsSearchResult.Hit> hits,
                                  List<Map<String, Object>> items) {
        if (query == null || query.isBlank()) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            Map<String, String> snippets = normalizeOcsHighlight(i < hits.size() ? hits.get(i).getHighlight() : null);
            if (snippets.isEmpty()) {
                snippets = searchHighlighter.highlight(query, item);
            }
            if (!snippets.isEmpty()) {
                item.put("highlight", snippets);
            }
        }
    }

    /** OCS index fields → the goods-list item fields the SPA renders. */
    private static final Map<String, String> OCS_HIGHLIGHT_FIELD_NAMES =
            Map.of("title", "name", "description", "brief", "name", "name", "brief", "brief");

    private Map<String, String> normalizeOcsHighlight(Map<String, Object> ocsHighlight) {
        Map<String, String> snippets = new LinkedHashMap<>();
        if (ocsHighlight == null) {
            return snippets;
        }
        for (Map.Entry<String, Object> entry : ocsHighlight.entrySet()) {
            String field = OCS_HIGHLIGHT_FIELD_NAMES.get(entry.getKey());
            if (field == null || !(entry.getValue() instanceof String)) {
                continue;
            }
            String snippet = (String) entry.getValue();
            // Contract: matches wrapped in <em> only — drop any other markup a searcher emits.
            snippet = snippet.replaceAll("<(?!/?em>)[^<>]*>", "");
            if (!snippet.isBlank()) {
                snippets.put(field, snippet);
            }
        }
        return snippets;
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
            // Live flash-deal extras (present only while a deal is live): countdown + claimed bar.
            if (data.get("deal_active") instanceof Number && ((Number) data.get("deal_active")).intValue() == 1) {
                item.put("dealActive", true);
                if (data.get("deal_end_epoch") instanceof Number) {
                    item.put("dealEndEpoch", ((Number) data.get("deal_end_epoch")).longValue());
                }
                if (data.get("deal_claimed_pct") instanceof Number) {
                    item.put("dealClaimedPct", ((Number) data.get("deal_claimed_pct")).intValue());
                }
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
