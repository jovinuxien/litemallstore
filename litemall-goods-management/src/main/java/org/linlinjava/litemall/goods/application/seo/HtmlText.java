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
        s = decodeEntities(s);
        // Post-decode: entities may have re-materialised angle brackets — drop them.
        s = s.replace('<', ' ').replace('>', ' ');
        s = CONTROL.matcher(s).replaceAll(" ");
        return WHITESPACE.matcher(s).replaceAll(" ").trim();
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