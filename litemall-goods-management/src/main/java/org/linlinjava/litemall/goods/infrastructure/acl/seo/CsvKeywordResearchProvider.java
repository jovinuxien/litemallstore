package org.linlinjava.litemall.goods.infrastructure.acl.seo;

import org.linlinjava.litemall.goods.application.seo.KeywordResearchProvider;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSeoResearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The same {@link KeywordResearchProvider} port, served from a CSV that a previous research
 * run already paid for.
 *
 * <p>This is the default source, and the reason is worth stating: keyword demand moves on a
 * scale of months, the platform itself caches a bought answer for 14 days, and every uncached
 * seed is a live purchase. Re-buying on every catalogue operation would be the expensive way
 * to learn nothing new. A file also works where the live path cannot — no network, no identity
 * provider, no provider balance — which is most of the time.
 *
 * <p>Expects the CLEANED export ({@code category-keywords-clean.csv}). The raw export keeps
 * permutation groups ("outdoor wall lamps", "exterior lamps wall", "wall lamps exterior" are
 * one term at one volume, not three), and feeding those in would make a single keyword look
 * like a trend. Rows flagged as competitor brands are skipped: real demand, but not something
 * to paste into product copy.
 *
 * <p>Loaded once, lazily, on first use. Fail-soft per the port contract: a missing, unreadable
 * or malformed file yields an empty list and one warning, never an exception at the caller.
 */
@Component
@ConditionalOnProperty(prefix = "litemall.seo-research", name = "source",
        havingValue = "file", matchIfMissing = true)
public class CsvKeywordResearchProvider implements KeywordResearchProvider {

    private static final Logger log = LoggerFactory.getLogger(CsvKeywordResearchProvider.class);

    private final LitemallSeoResearchProperties properties;

    private volatile Map<String, List<KeywordDemand>> byCategory;

    public CsvKeywordResearchProvider(LitemallSeoResearchProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<KeywordDemand> demandFor(String seed, int limit) {
        if (seed == null || seed.isBlank()) {
            return List.of();
        }
        Map<String, List<KeywordDemand>> index = index();
        List<KeywordDemand> hit = index.get(key(seed));
        if (hit == null || hit.isEmpty()) {
            return List.of();
        }
        return hit.size() <= limit ? hit : List.copyOf(hit.subList(0, Math.max(1, limit)));
    }

    private Map<String, List<KeywordDemand>> index() {
        Map<String, List<KeywordDemand>> local = byCategory;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (byCategory == null) {
                byCategory = load();
            }
            return byCategory;
        }
    }

    private Map<String, List<KeywordDemand>> load() {
        String configured = properties.getFile();
        if (configured == null || configured.isBlank()) {
            log.warn("seo research: source=file but litemall.seo-research.file is not set "
                    + "— no keyword data will be served");
            return Map.of();
        }
        Path path = Path.of(configured);
        if (!Files.isReadable(path)) {
            log.warn("seo research: keyword export {} is missing or unreadable "
                    + "— no keyword data will be served", path);
            return Map.of();
        }

        Map<String, List<KeywordDemand>> out = new HashMap<>();
        int rows = 0;
        int skippedBrand = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) {
                log.warn("seo research: keyword export {} is empty", path);
                return Map.of();
            }
            Map<String, Integer> col = new HashMap<>();
            List<String> names = splitCsv(header);
            for (int i = 0; i < names.size(); i++) {
                col.put(names.get(i).trim().toLowerCase(Locale.ROOT), i);
            }
            // 'variants_collapsed' is what actually distinguishes the two exports. Both
            // carry category/keyword/monthly_searches, so requiring only those would ACCEPT
            // the raw file — and the raw file keeps permutation groups, so a category would
            // silently serve six spellings of one term as its top six. The raw export has
            // 'rank' where the cleaned one has 'variants_collapsed' and 'flag'.
            for (String required : List.of("category", "keyword", "monthly_searches",
                    "variants_collapsed")) {
                if (!col.containsKey(required)) {
                    log.warn("seo research: keyword export {} has no '{}' column "
                                    + "— expected the CLEANED export (category-keywords-clean.csv)",
                            path, required);
                    return Map.of();
                }
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> f = splitCsv(line);
                String category = at(f, col.get("category"));
                String keyword = at(f, col.get("keyword"));
                if (category == null || keyword == null || category.isBlank() || keyword.isBlank()) {
                    continue;
                }
                String flag = at(f, col.get("flag"));
                if (flag != null && !flag.isBlank()) {
                    skippedBrand++;
                    continue;
                }
                out.computeIfAbsent(key(category), k -> new ArrayList<>())
                        .add(new KeywordDemand(keyword,
                                asInt(at(f, col.get("monthly_searches"))),
                                asDecimal(at(f, col.get("competition"))),
                                asInt(at(f, col.get("difficulty")))));
                rows++;
            }
        } catch (Exception e) {
            log.warn("seo research: could not read keyword export {} ({}) "
                    + "— no keyword data will be served", path, e.getMessage());
            return Map.of();
        }

        for (List<KeywordDemand> terms : out.values()) {
            // Highest demand first; unknown volume sorts last rather than as zero.
            terms.sort(Comparator.comparing(
                    (KeywordDemand d) -> d.monthlySearches() == null
                            ? Integer.MIN_VALUE : d.monthlySearches()).reversed());
        }
        log.info("seo research: loaded {} keywords for {} categories from {} ({} brand rows skipped)",
                rows, out.size(), path, skippedBrand);
        return Map.copyOf(out);
    }

    /** Category names are matched case- and whitespace-insensitively. */
    private static String key(String category) {
        return category.trim().toLowerCase(Locale.ROOT);
    }

    private static String at(List<String> fields, Integer i) {
        return i == null || i >= fields.size() ? null : fields.get(i);
    }

    /** Absent, empty and the literal "None" all mean UNKNOWN — never 0. */
    private static Integer asInt(String v) {
        if (v == null || v.isBlank() || "None".equals(v)) {
            return null;
        }
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal asDecimal(String v) {
        if (v == null || v.isBlank() || "None".equals(v)) {
            return null;
        }
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Minimal RFC-4180 split: quoted fields, doubled quotes inside them. Enough for this
     * file, whose category names carry commas ("Kitchen, Dining &amp; Bar") — a naive
     * split on comma silently shifts every later column on those rows.
     */
    static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
