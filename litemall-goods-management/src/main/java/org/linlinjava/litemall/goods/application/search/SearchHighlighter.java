package org.linlinjava.litemall.goods.application.search;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * App-side snippet highlighter for search hits. The deployed OCS searcher has no highlighting
 * (its {@code ResultHit} model carries no highlight field; {@code highlight=true} is ignored —
 * live-probed 2026-07-24), so the Wave-9 per-hit {@code highlight} map is produced here instead:
 * query terms are matched case-insensitively against the hit's display fields and wrapped in
 * {@code <em>} per the cross-module contract. The SPA sanitizes (only {@code em}/{@code mark}
 * survive; everything else is escaped), so snippets are the raw field text plus {@code <em>}
 * markers — no escaping happens on this side.
 */
@Component
public class SearchHighlighter {

    /** The goods-list item fields eligible for snippets — also the contract's map keys. */
    private static final String[] HIGHLIGHT_FIELDS = {"name", "brief"};

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final int MIN_TERM_LENGTH = 2;
    private static final int MAX_TERMS = 8;

    /**
     * Snippets for one goods-list item: {@code {field: text-with-<em>-wrapped-matches}}, only
     * fields with at least one match present. Empty map (never null) when the query is blank or
     * nothing matched — callers omit the {@code highlight} key entirely in that case.
     */
    public Map<String, String> highlight(String query, Map<String, Object> item) {
        Map<String, String> snippets = new LinkedHashMap<>();
        List<String> terms = tokenize(query);
        if (terms.isEmpty() || item == null) {
            return snippets;
        }
        for (String field : HIGHLIGHT_FIELDS) {
            Object value = item.get(field);
            if (!(value instanceof String)) {
                continue;
            }
            String text = (String) value;
            String snippet = highlightText(text, terms);
            if (snippet != null) {
                snippets.put(field, snippet);
            }
        }
        return snippets;
    }

    /** Query terms worth matching: lowercased, deduped, single-character noise dropped. */
    private List<String> tokenize(String query) {
        List<String> terms = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return terms;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : TOKEN_SPLIT.split(query.toLowerCase(Locale.ROOT))) {
            if (raw.length() >= MIN_TERM_LENGTH && seen.add(raw)) {
                terms.add(raw);
                if (terms.size() >= MAX_TERMS) {
                    break;
                }
            }
        }
        return terms;
    }

    /** The text with every term occurrence {@code <em>}-wrapped, or null when nothing matched. */
    private String highlightText(String text, List<String> terms) {
        if (text.isBlank()) {
            return null;
        }
        boolean[] marked = new boolean[text.length()];
        String lower = text.toLowerCase(Locale.ROOT);
        boolean any = false;
        for (String term : terms) {
            int from = 0;
            int idx;
            while ((idx = lower.indexOf(term, from)) >= 0) {
                // Lowercasing can change length for a few locale-specific characters; clamp so a
                // late match near the end can never index past the original text.
                int end = Math.min(idx + term.length(), marked.length);
                for (int i = idx; i < end; i++) {
                    marked[i] = true;
                    any = true;
                }
                from = idx + term.length();
            }
        }
        if (!any) {
            return null;
        }
        StringBuilder out = new StringBuilder(text.length() + 32);
        boolean open = false;
        for (int i = 0; i < text.length(); i++) {
            if (marked[i] && !open) {
                out.append("<em>");
                open = true;
            } else if (!marked[i] && open) {
                out.append("</em>");
                open = false;
            }
            out.append(text.charAt(i));
        }
        if (open) {
            out.append("</em>");
        }
        return out.toString();
    }
}
