package org.linlinjava.litemall.goods.application.season;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A season's term list, split into the terms that DISCOVER candidates and the terms that EXCLUDE
 * them, plus the one title check both rely on. Pure: no Spring, no I/O.
 *
 * <h2>Why a title check at all</h2>
 * Discovery runs each term through the search index, and the index is built to be generous: it
 * matches descriptions and category names as well as titles, tolerates typos
 * ({@code fuzziness: AUTO}), and falls back to relaxed and n-gram strategies when the exact query
 * finds nothing. That generosity is right for a shopper and wrong for a curator. The first live
 * autumn rail carried an "All-Season Sofa Cover" that arrived through relaxed relevance and a
 * "Summer Cooling Air-Conditioning Blanket" that matched {@code blanket} exactly. So a hit only
 * counts when the term is actually IN THE TITLE, at a word boundary, and no exclusion term is.
 *
 * <h2>Exclusions ride the same list</h2>
 * A term starting with {@code -} excludes rather than discovers: {@code "-summer"} on the autumn
 * rule drops any title containing "summer". Keeping them in the one {@code terms} JSON array
 * means no migration, no new admin field, and the existing {@code PUT /season-rules/{key}}
 * already edits them. The convention is documented in the spec and pinned by tests.
 *
 * <h2>What "in the title" means</h2>
 * Case-insensitive, at a word boundary on both sides (so {@code rug} does not match "drug" and
 * {@code fall} does not match "waterfall"), with a plural tolerance of a trailing {@code s} or
 * {@code es} (so {@code blanket} matches "Blankets"). Multi-word terms match across any run of
 * whitespace. Punctuation counts as a boundary, so {@code -anti-fall} excludes "Anti-Fall Mat".
 */
public final class SeasonTerms {

    /** The prefix that turns a term into an exclusion. */
    public static final String EXCLUSION_PREFIX = "-";

    private final List<String> discoveryTerms;
    private final List<String> exclusionTerms;

    private SeasonTerms(List<String> discoveryTerms, List<String> exclusionTerms) {
        this.discoveryTerms = Collections.unmodifiableList(discoveryTerms);
        this.exclusionTerms = Collections.unmodifiableList(exclusionTerms);
    }

    /** Split a raw term list. Blank entries and a bare {@code -} are ignored. */
    public static SeasonTerms of(List<String> raw) {
        List<String> discover = new ArrayList<>();
        List<String> exclude = new ArrayList<>();
        if (raw != null) {
            for (String entry : raw) {
                if (entry == null) {
                    continue;
                }
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith(EXCLUSION_PREFIX)) {
                    String term = trimmed.substring(EXCLUSION_PREFIX.length()).trim();
                    if (!term.isEmpty()) {
                        exclude.add(term);
                    }
                } else {
                    discover.add(trimmed);
                }
            }
        }
        return new SeasonTerms(discover, exclude);
    }

    /** Terms run through the index to find candidates. */
    public List<String> discoveryTerms() {
        return discoveryTerms;
    }

    /** Terms whose presence in a title rejects the candidate. */
    public List<String> exclusionTerms() {
        return exclusionTerms;
    }

    /** True when any exclusion term appears in the title. A null title excludes nothing. */
    public boolean excludes(String title) {
        if (title == null) {
            return false;
        }
        for (String term : exclusionTerms) {
            if (containsTerm(title, term)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code term} appears in {@code title} as a whole word (plural-tolerant).
     *
     * <p>A null or blank title never matches: a hit whose title the search did not return cannot be
     * verified, and an unverifiable hit is dropped rather than trusted. Fails closed, like the
     * uncosted gate.
     */
    public static boolean containsTerm(String title, String term) {
        if (title == null || title.isBlank() || term == null || term.isBlank()) {
            return false;
        }
        return pattern(term).matcher(title).find();
    }

    private static Pattern pattern(String term) {
        String[] words = term.trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder regex = new StringBuilder("(?<![\\p{L}\\p{N}])");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                regex.append("\\s+");
            }
            regex.append(Pattern.quote(words[i]));
        }
        regex.append("(?:e?s)?(?![\\p{L}\\p{N}])");
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
