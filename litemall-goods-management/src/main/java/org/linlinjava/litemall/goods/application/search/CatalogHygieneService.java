package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.application.seo.MetaCatalogFeedService;
import org.linlinjava.litemall.goods.application.seo.SitemapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Wave-25 catalog hygiene, run on demand from the admin surface (idempotent — re-runs are
 * no-ops once the catalog is clean):
 *
 * <ul>
 *   <li><b>Units:</b> CJK unit glyphs ({@code 件}/{@code 盒}/…, seeded by the old adapter default
 *       and upstream data) render beside the € price on the PDP — normalized to blank. The
 *       adapter no longer stamps them on new promotes; this pass cleans the existing rows.</li>
 *   <li><b>Supplier prefixes:</b> CJ leaks its own logistics note into the customer-facing title
 *       ({@code "Support Pan European：..."} — 68 live products on 2026-08-26), where it shows on
 *       the PDP and in the merchant feed and reads as an unfinished listing. Stripped from the
 *       name; the search keywords column keeps the original text, so recall is unchanged.</li>
 *   <li><b>Unreadable on-sale names</b> poison merchant-feed review: renamed from their CJ
 *       snapshot's English title when it is clean, else OFF-SALED (reversible, the standard
 *       retirement semantics) with the reason logged. Covers Chinese names and, since
 *       2026-08-26, Spanish and German ones — 38 live products whose titles no customer in this
 *       market can read, and which fail feed review for inconsistent language. Renames/off-sales
 *       propagate per row to the OCS index, and the sitemap + feed artifacts are rebuilt at the
 *       end (the slugged links derive from the name).</li>
 * </ul>
 *
 * <p>Returns honest counts; a per-row failure is logged and skipped so a single bad row can't
 * abort the pass (that row stays dirty and is retried on the next run).
 */
@Service
public class CatalogHygieneService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogHygieneService.class);

    private static final int PAGE_SIZE = 200;
    private static final int NAME_MAX = 127;

    /** Leading supplier logistics note; ASCII or fullwidth (U+FF1A) colon, optional. */
    private static final Pattern SUPPLIER_PREFIX =
            Pattern.compile("^\\s*Support\\s+Pan\\s+European\\s*[:\\uFF1A]?\\s*", Pattern.CASE_INSENSITIVE);

    private static final Pattern WORD = Pattern.compile("[\\p{L}]+");

    /**
     * Any letter outside ASCII — accents, umlauts, eszett. Java character-class INTERSECTION,
     * which needs the nested form: {@code [^\p{ASCII}&&\p{L}]} would negate the intersection and
     * match every space and digit instead.
     */
    private static final Pattern NON_ASCII_LETTER = Pattern.compile("[\\p{L}&&[^\\p{ASCII}]]");

    /**
     * Unambiguous Spanish and German function words. Multi-letter only, and nothing that doubles
     * as an English product token — see {@link #usableTitle} for why {@code y}/{@code a}/
     * {@code la}/{@code el} are absent.
     */
    private static final Set<String> FOREIGN_FUNCTION_WORDS = Set.of(
            // Spanish
            "para", "con", "sin", "del", "los", "las", "una", "por", "que", "mas", "más",
            "desde", "hasta", "muy", "este", "esta", "como", "todo",
            // German
            "für", "fur", "mit", "und", "der", "die", "das", "aus", "zum", "zur", "ohne",
            "stück", "stuck", "oder", "auch", "sehr", "beim", "vom", "ein", "eine", "einem");

    private final LitemallGoodsService goodsService;
    private final LitemallCjProductService cjProductStore;
    private final SearchReindexService reindexService;
    private final SitemapService sitemapService;
    private final MetaCatalogFeedService metaCatalogFeedService;

    public CatalogHygieneService(LitemallGoodsService goodsService,
                                 LitemallCjProductService cjProductStore,
                                 SearchReindexService reindexService,
                                 SitemapService sitemapService,
                                 MetaCatalogFeedService metaCatalogFeedService) {
        this.goodsService = goodsService;
        this.cjProductStore = cjProductStore;
        this.reindexService = reindexService;
        this.sitemapService = sitemapService;
        this.metaCatalogFeedService = metaCatalogFeedService;
    }

    /**
     * Full hygiene pass; returns
     * {scanned, unitsNormalized, prefixesStripped, renamed, offSaled, failed}.
     */
    public Map<String, Object> run() {
        int scanned = 0;
        int unitsNormalized = 0;
        int prefixesStripped = 0;
        int renamed = 0;
        int offSaled = 0;
        int failed = 0;

        int page = 1;
        while (true) {
            List<LitemallGoods> batch = goodsService.querySelective(
                    null, null, null, page, PAGE_SIZE, "id", "asc");
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (LitemallGoods goods : batch) {
                if (goods.getId() == null) {
                    continue;
                }
                scanned++;
                try {
                    Outcome outcome = fixOne(goods);
                    unitsNormalized += outcome.unitNormalized ? 1 : 0;
                    prefixesStripped += outcome.prefixStripped ? 1 : 0;
                    renamed += outcome.renamed ? 1 : 0;
                    offSaled += outcome.offSaled ? 1 : 0;
                } catch (RuntimeException ex) {
                    failed++;
                    LOGGER.warn("catalog hygiene failed for goods {} (skipped, retried next run): {}",
                            goods.getId(), ex.getMessage());
                }
            }
            if (batch.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }

        // A stripped prefix changes the name, and the slugged PDP link + feed title derive
        // from it — so it rebuilds the artifacts for the same reason a rename does.
        if (renamed > 0 || offSaled > 0 || prefixesStripped > 0) {
            try {
                sitemapService.rebuild();
                metaCatalogFeedService.rebuild();
            } catch (RuntimeException ex) {
                LOGGER.warn("catalog hygiene: sitemap/feed rebuild failed (next nightly covers it): {}",
                        ex.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanned", scanned);
        result.put("unitsNormalized", unitsNormalized);
        result.put("prefixesStripped", prefixesStripped);
        result.put("renamed", renamed);
        result.put("offSaled", offSaled);
        result.put("failed", failed);
        LOGGER.info("catalog hygiene: {}", result);
        return result;
    }

    private record Outcome(boolean unitNormalized, boolean prefixStripped, boolean renamed,
                           boolean offSaled) {
    }

    private Outcome fixOne(LitemallGoods goods) {
        LitemallGoods patch = new LitemallGoods();
        patch.setId(goods.getId());
        boolean unitNormalized = false;
        boolean prefixStripped = false;
        boolean nameRenamed = false;
        boolean offSaled = false;

        if (containsHan(goods.getUnit())) {
            patch.setUnit("");
            unitNormalized = true;
        }

        // The name this pass reasons about from here on. Prefix stripping runs FIRST and
        // deliberately: "Support Pan European：Battery Hand Circular Saw" is a perfectly good
        // English title wearing a supplier's logistics note, and judging its language before
        // removing the note would off-sale a product that only needed a trim.
        String currentName = goods.getName();
        String stripped = stripSupplierPrefix(currentName);
        if (stripped != null && !stripped.equals(currentName)) {
            currentName = stripped;
            patch.setName(stripped);
            // keywords is the SEARCH column (querySelective LIKE-matches it), so it keeps the
            // untrimmed text — a shopper searching the old string still finds the product.
            prefixStripped = true;
            LOGGER.info("catalog hygiene: goods {} supplier prefix stripped ('{}' -> '{}')",
                    goods.getId(), goods.getName(), stripped);
        }

        // Names in a language the storefront does not speak only matter while the product is
        // customer-visible; off-sale rows keep their name (reversible retirement semantics)
        // until they ever come back.
        //
        // Chinese and Spanish/German are the same defect with the same remedy — a title no
        // shopper in this market can read, and one that fails merchant-feed review for
        // inconsistent language — so they share the rename-or-retire path below.
        if (Boolean.TRUE.equals(goods.getIsOnSale()) && !usableTitle(currentName)) {
            String replacement = englishTitleFromSnapshot(goods);
            if (replacement != null) {
                patch.setName(replacement);
                patch.setKeywords(replacement);
                nameRenamed = true;
                LOGGER.info("catalog hygiene: goods {} renamed from CJ snapshot title ('{}' -> '{}')",
                        goods.getId(), currentName, replacement);
            } else {
                patch.setIsOnSale(Boolean.FALSE);
                offSaled = true;
                LOGGER.info("catalog hygiene: goods {} off-saled — unreadable name '{}' with no "
                        + "clean English snapshot title to rename from", goods.getId(), currentName);
            }
        }

        if (!unitNormalized && !prefixStripped && !nameRenamed && !offSaled) {
            return new Outcome(false, false, false, false);
        }
        goodsService.updateById(patch);
        // Name and on-sale state live in the OCS document — converge it row by row (an off-sale
        // flip deletes the document; a plain unit fix reindexes harmlessly).
        reindexService.reindexGoods(goods.getId());
        return new Outcome(unitNormalized, prefixStripped, nameRenamed, offSaled);
    }

    /**
     * The CJ snapshot's (English) title, cleaned for use as the goods name; null when unusable.
     *
     * <p>The snapshot carries the supplier's raw string, so it is trimmed and language-checked
     * by exactly the same rules as the live name — otherwise the rename path would "fix" a
     * Spanish title by writing the same Spanish title back.
     */
    private String englishTitleFromSnapshot(LitemallGoods goods) {
        if (goods.getCjPid() == null || goods.getCjPid().isBlank()) {
            return null;
        }
        LitemallCjProduct snapshot = cjProductStore.findByPid(goods.getCjPid());
        if (snapshot == null || snapshot.getTitle() == null) {
            return null;
        }
        String title = stripSupplierPrefix(snapshot.getTitle().trim());
        if (title == null || title.isEmpty() || !usableTitle(title) || title.equals(goods.getName())) {
            return null;
        }
        return title.length() <= NAME_MAX ? title : title.substring(0, NAME_MAX);
    }

    /**
     * A supplier logistics note that leaks into the customer-facing title, e.g.
     * {@code "Support Pan European：Battery Stapler 3.6V"}. Measured on the live feed
     * 2026-08-26: 68 on-sale products, visible on the PDP and in the merchant feed, where it
     * reads as an unfinished listing. The colon may be ASCII or the CJK fullwidth form (U+FF1A),
     * which is what CJ actually sends.
     *
     * @return the title without the prefix; the input unchanged when no prefix is present, or
     *         null for a null input
     */
    static String stripSupplierPrefix(String name) {
        if (name == null) {
            return null;
        }
        String out = SUPPLIER_PREFIX.matcher(name).replaceFirst("").trim();
        // Never trade a bad title for an empty one: a name that is ONLY the prefix keeps it,
        // and the language check below then sends it down the rename-or-retire path.
        return out.isEmpty() ? name : out;
    }

    /**
     * Whether a title is readable by this storefront's customers — i.e. not Chinese, and not
     * Spanish or German.
     *
     * <p>Detection is deliberately conservative, because a false positive off-sales a product
     * that was fine. It requires either two distinct foreign function words, or one plus a
     * non-ASCII letter; single-letter and ambiguous tokens ({@code y}, {@code a}, {@code la},
     * {@code el}) are excluded, since English product titles use them as model codes and
     * connectors ("Accessory Y Retractable Cable"). Validated against all 4,055 live titles on
     * 2026-08-26: 38 flagged, every one genuinely Spanish or German, and accented English such
     * as "Macramé Hanging Basket" correctly left alone.
     */
    static boolean usableTitle(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (containsHan(name)) {
            return false;
        }
        int distinct = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.regex.Matcher m = WORD.matcher(name);
        while (m.find()) {
            String word = m.group().toLowerCase(Locale.ROOT);
            if (FOREIGN_FUNCTION_WORDS.contains(word) && seen.add(word)) {
                distinct++;
            }
        }
        if (distinct == 0) {
            return true;
        }
        return distinct < 2 && !NON_ASCII_LETTER.matcher(name).find();
    }

    static boolean containsHan(String value) {
        return value != null && value.codePoints()
                .anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }
}
