package org.linlinjava.litemall.goods.application.seo;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plain-text cleanup for feed surfaces. CJ briefs may embed raw HTML (goods 10000001
 * carries an {@code <img>} inside {@code brief}) and entity soup; catalogue consumers
 * (Meta Commerce Manager) want clean single-line prose. Angle brackets are removed even
 * when they arrive as entities: a stray {@code <} in a feed field reads as leaked markup
 * to validators, and nothing a product description needs is lost with them.
 */
final class HtmlText {

    private static final Pattern SCRIPT_STYLE = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    /**
     * An opening bracket with no closing bracket before end-of-string: a tag cut in half. Fields
     * are stored truncated ({@code goods.brief} is capped at 255 chars), and a half-tag is not a
     * tag, so {@link #TAG} cannot match it and its attributes survive as prose.
     *
     * <p>The bracket must be followed by a LETTER (or a closing slash) — i.e. something that could
     * be a tag name. Matching a bare "&lt;" would eat the rest of any sentence containing a
     * less-than sign: "Rated 5 &lt; 10 lux for garden use" would truncate to "Rated 5".
     */
    private static final Pattern TRAILING_PARTIAL_TAG = Pattern.compile("</?[a-zA-Z][^>]*$");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]{1,6});");
    private static final Pattern CONTROL = Pattern.compile("[\\u0000-\\u001F\\u007F]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private HtmlText() {
    }

    /** Tag-strip, entity-decode, control-strip, whitespace-collapse; never null. */
    static String clean(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = SCRIPT_STYLE.matcher(raw).replaceAll(" ");
        s = TAG.matcher(s).replaceAll(" ");
        // DOUBLE-ENCODED markup (&lt;img src="..."&gt;) is not a tag until it is decoded, so a
        // single strip-then-decode leaves the attribute text behind as prose. That shipped
        // `img src="https://oss-cf.cjdropshipping.com/..."` as the DESCRIPTION of 233 feed rows
        // (8%), which is a Merchant Center data-quality rejection waiting to happen. Decode and
        // re-strip until stable; bounded, because a hostile input could otherwise re-encode
        // forever.
        for (int pass = 0; pass < 3; pass++) {
            String decoded = decodeEntities(s);
            String stripped = TAG.matcher(SCRIPT_STYLE.matcher(decoded).replaceAll(" ")).replaceAll(" ");
            if (stripped.equals(s)) {
                break;
            }
            s = stripped;
        }
        // A field truncated mid-tag leaves an unclosed opener the TAG pattern cannot match. Drop
        // the fragment rather than letting `img src="https://..."` become the product description
        // — which is what 233 feed rows (8%) were shipping to Merchant Center.
        s = TRAILING_PARTIAL_TAG.matcher(s).replaceAll(" ");
        // Whatever angle brackets survive are stray text, not markup.
        s = s.replace('<', ' ').replace('>', ' ');
        s = CONTROL.matcher(s).replaceAll(" ");
        return WHITESPACE.matcher(s).replaceAll(" ").trim();
    }

    /**
     * Wave 26: turn a raw CJ supplier listing into something that reads like store copy.
     *
     * <p>Measured on the live feed (2026-08-16): 47% of in-band descriptions carried at least one
     * marketplace artefact — 30.5% opened with a bare "Description:"/"Features:" label, 4.4% used
     * CJK punctuation (【】、！) in English text, and some still had markdown emphasis. To a Merchant
     * Center reviewer, and to a shopper, that reads as scraped supplier text.
     *
     * <p>Deliberately NOT done here:
     * <ul>
     *   <li><b>Unit conversion.</b> Imperial-first copy ("66 lbs.") is wrong for a EUR store, but
     *       most of it already carries metric in parentheses, and generating numbers risks getting
     *       them wrong. A wrong measurement is worse than an American-sounding one.</li>
     *   <li><b>De-shouting ALL-CAPS runs.</b> It mangles USB, LED, PSI, COVID-19 and brand names,
     *       for 1.9% of rows.</li>
     *   <li><b>Translating German listings.</b> DE-warehoused products carry CJ's German copy,
     *       which is an asset in this market, not an artefact.</li>
     * </ul>
     *
     * <p>Nothing here touches stored data — the supplier original stays the source of truth and
     * this runs at render time, so a bad rule can be fixed by redeploying rather than by a
     * migration.
     */
    static String tidyDescription(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        String t = s;
        // 1. CJK punctuation in otherwise-English copy. 【Label】Body -> "Label: Body".
        t = t.replaceAll("【\\s*([^】]{1,60}?)\\s*】\\s*", "$1: ");
        t = t.replace("、", ", ").replace("，", ", ").replace("。", ". ")
             .replace("！", "! ").replace("？", "? ").replace("；", "; ")
             .replace("：", ": ").replace("（", " (").replace("）", ") ");
        // 2. Markdown emphasis that survived the supplier's own editor.
        t = t.replaceAll("\\*\\*([^*]+)\\*\\*", "$1").replaceAll("__([^_]+)__", "$1");
        // 3. Marketplace bullet labels: "[High-Precision] Engineered..." -> "High-Precision: Engineered..."
        t = t.replaceAll("\\[\\s*([\\p{L}\\p{N}][^\\]]{2,48})\\s*\\]\\s*", "$1: ");
        // 4. A bare section label at the very start is a form field, not a sentence. Only at the
        //    start: "Features:" mid-text is legitimately introducing a list.
        t = t.replaceFirst("(?i)^\\s*(product information|product details|description|features?|"
                + "specifications?|produktbeschreibung|technische daten)\\s*:\\s*", "");
        // Collapse the spacing the substitutions above introduce, and tidy space-before-punctuation.
        t = t.replaceAll("\\s+([,.;:!?])", "$1");
        t = WHITESPACE.matcher(t).replaceAll(" ").trim();
        return t;
    }

    /** SHOUTY ALL-CAPS names read as spam in feeds — re-case each word once. */
    static String uncapsIfShouty(String s) {
        if (s.isEmpty()) {
            return s;
        }
        boolean hasLetter = s.chars().anyMatch(Character::isLetter);
        if (!hasLetter || !s.equals(s.toUpperCase(Locale.ROOT)) || s.equals(s.toLowerCase(Locale.ROOT))) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length());
        boolean atWordStart = true;
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append(atWordStart && Character.isLetter(c) ? Character.toUpperCase(c) : c);
            atWordStart = !Character.isLetter(c) && !Character.isDigit(c);
        }
        return out.toString();
    }

    /** Truncate on a word boundary at or before {@code max} characters. */
    static String truncateAtWord(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        int cut = s.lastIndexOf(' ', max);
        if (cut <= 0) {
            cut = max;
        }
        return s.substring(0, cut).trim();
    }

    private static String decodeEntities(String s) {
        if (s.indexOf('&') < 0) {
            return s;
        }
        Matcher m = NUMERIC_ENTITY.matcher(s);
        StringBuilder decoded = new StringBuilder(s.length());
        while (m.find()) {
            String replacement;
            try {
                int code = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
                replacement = Character.isValidCodePoint(code) && !Character.isISOControl(code)
                        ? new String(Character.toChars(code))
                        : " ";
            } catch (IllegalArgumentException e) {
                replacement = " ";
            }
            m.appendReplacement(decoded, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(decoded);
        // &amp; last, so double-escaped entities resolve to their literal text form.
        return decoded.toString()
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }
}