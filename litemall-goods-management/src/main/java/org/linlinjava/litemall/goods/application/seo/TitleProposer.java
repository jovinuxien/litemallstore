package org.linlinjava.litemall.goods.application.seo;

import org.linlinjava.litemall.goods.application.seo.KeywordResearchProvider.KeywordDemand;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Proposes a shorter product title, and says what search demand backs it.
 *
 * <p><strong>What this deliberately does NOT do:</strong> it does not write a title from the
 * keyword. Rearranging a supplier's machine-written name around a bought term produces things
 * like "garden tools Rechargeable Device For Smoothing Calluses" — grammatical debris that
 * reads worse than the original and would go out under the shop's name. The keyword set also
 * carries real noise (competitor brands, and homonyms like "friday night lights" under Night
 * Lights), so generating copy from it unattended would publish that noise.
 *
 * <p>What it does instead: truncate on a word boundary, which is the actual defect — Google
 * cuts titles near 60 characters, and 2,293 of this catalogue's 3,718 titles are longer than
 * 70 — and attach the demand evidence so a human edits with the numbers in front of them.
 * Every proposal is a suggestion; nothing here writes to the catalogue.
 */
public final class TitleProposer {

    /** Google truncates a result title near here. Not a hard API limit — a legibility one. */
    public static final int DEFAULT_MAX_LENGTH = 60;

    /**
     * Below this a truncation has cut away the product itself ("Rechargeable Device For" tells
     * a shopper nothing), so the original stands and the row is marked as needing a human.
     */
    private static final int MIN_USEFUL_LENGTH = 20;

    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");

    /** Words too common to prove a keyword is really present in a title. */
    private static final Set<String> STOP = Set.of(
            "and", "the", "for", "of", "a", "an", "in", "to", "with", "on", "by", "or");

    private TitleProposer() {
    }

    /**
     * @param maxLength target length; titles at or under it need no proposal
     * @param terms     the category's demand terms, highest first; may be empty
     */
    public static Proposal propose(String currentTitle, List<KeywordDemand> terms, int maxLength) {
        String clean = HtmlText.uncapsIfShouty(HtmlText.clean(currentTitle));
        if (clean.isEmpty()) {
            return null;
        }
        boolean overLength = clean.length() > maxLength;

        KeywordDemand matched = bestMatch(clean, terms);
        String proposed = overLength ? HtmlText.truncateAtWord(clean, maxLength) : clean;

        // HtmlText.truncateAtWord falls back to a HARD cut at `max` when there is no space at
        // or before it (`cut <= 0 -> cut = max`), so a single long leading word comes back
        // sliced mid-word: "Antidisestablishmentarianism ..." at 20 becomes
        // "Antidisestablishment". That helper is shared with the merchant feed, so it is not
        // the thing to change — but a mangled word must never be offered as a title.
        boolean midWordCut = !clean.equals(proposed)
                && !(clean.startsWith(proposed)
                     && Character.isWhitespace(clean.charAt(proposed.length())));
        boolean tooShort = proposed.length() < MIN_USEFUL_LENGTH;
        if (tooShort || midWordCut) {
            // Truncation destroyed the meaning. Hand the original back rather than propose
            // something worse than what is live.
            proposed = clean;
        }

        boolean keywordSurvives = matched != null && containsAllWords(proposed, matched.term());

        return new Proposal(clean, clean.length(), proposed, proposed.length(), overLength,
                !overLength || tooShort || midWordCut || (matched != null && !keywordSurvives),
                matched == null ? null : matched.term(),
                matched == null ? null : matched.monthlySearches(),
                keywordSurvives);
    }

    /**
     * The highest-demand term the title already contains.
     *
     * <p>Terms arrive sorted by demand, so the first full match is the best one. "Contains"
     * means every significant word of the term appears in the title — not a substring test,
     * which would match "lamps" inside "lampshade" and claim demand the title has not earned.
     */
    private static KeywordDemand bestMatch(String title, List<KeywordDemand> terms) {
        if (terms == null) {
            return null;
        }
        for (KeywordDemand term : terms) {
            if (term != null && term.term() != null && containsAllWords(title, term.term())) {
                return term;
            }
        }
        return null;
    }

    private static boolean containsAllWords(String haystack, String phrase) {
        Set<String> words = words(haystack);
        Set<String> needed = words(phrase);
        needed.removeAll(STOP);
        return !needed.isEmpty() && words.containsAll(needed);
    }

    private static Set<String> words(String s) {
        Set<String> out = new LinkedHashSet<>();
        for (String w : NON_WORD.split(s.toLowerCase(Locale.ROOT))) {
            if (!w.isEmpty()) {
                out.add(w);
            }
        }
        return out;
    }

    /**
     * @param needsReview true when a human should look before applying: the truncation ate the
     *                    matched keyword, or destroyed the meaning, or there was nothing to fix
     */
    public record Proposal(String currentTitle, int currentLength, String proposedTitle,
                           int proposedLength, boolean overLength, boolean needsReview,
                           String matchedKeyword, Integer matchedKeywordVolume,
                           boolean keywordSurvives) {
    }
}
