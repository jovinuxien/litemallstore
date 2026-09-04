package org.linlinjava.litemall.goods.application.season;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.db.dao.LitemallSeasonCandidateMapper;
import org.linlinjava.litemall.db.dao.LitemallSeasonRuleMapper;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.db.domain.LitemallSeasonCandidate;
import org.linlinjava.litemall.db.domain.LitemallSeasonRule;
import org.linlinjava.litemall.db.service.LitemallGoodsProductService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.application.search.SearchService;
import org.linlinjava.litemall.goods.domain.service.elastic.EuStockSignalResolver;
import org.linlinjava.litemall.goods.domain.service.elastic.SeasonSignalResolver;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSeasonProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs a scoring pass over every enabled season.
 *
 * <p><b>Discovery reuses the index rather than inventing matching.</b> Each season's terms are run
 * through OCS, the hits become the candidate set, and the global gates are then applied with real
 * cost and stock read from the database. So the scorer reads the index to find candidates and
 * writes a signal back into it — mildly circular, and much cheaper than a second matching engine
 * that would have to be kept in step with the first.
 *
 * <p><b>Every season is scored, not only the running one.</b> That is what lets the winter page be
 * populated before anyone activates it, and it is why membership is multi-valued.
 */
@Service
public class SeasonScoringService {

    private static final Logger log = LoggerFactory.getLogger(SeasonScoringService.class);

    private final LitemallSeasonRuleMapper ruleMapper;
    private final LitemallSeasonCandidateMapper candidateMapper;
    private final LitemallGoodsService goodsService;
    private final LitemallGoodsProductService productService;
    private final SearchService searchService;
    private final SeasonSignalResolver signalResolver;
    private final EuStockSignalResolver euStockSignalResolver;
    private final LitemallSeasonProperties properties;
    private final LitemallGoodsProperties goodsProperties;
    private final ObjectMapper objectMapper;

    public SeasonScoringService(LitemallSeasonRuleMapper ruleMapper,
                                LitemallSeasonCandidateMapper candidateMapper,
                                LitemallGoodsService goodsService,
                                LitemallGoodsProductService productService,
                                SearchService searchService,
                                SeasonSignalResolver signalResolver,
                                EuStockSignalResolver euStockSignalResolver,
                                LitemallSeasonProperties properties,
                                LitemallGoodsProperties goodsProperties,
                                ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.candidateMapper = candidateMapper;
        this.goodsService = goodsService;
        this.productService = productService;
        this.searchService = searchService;
        this.signalResolver = signalResolver;
        this.euStockSignalResolver = euStockSignalResolver;
        this.properties = properties;
        this.goodsProperties = goodsProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * What one pass did, per season — returned so a manual run reports honestly.
     *
     * <p>The three {@code discarded*} counts are HITS, not products: a product can be discarded
     * under two terms and counted twice. They exist so a rail that looks thin can be traced to the
     * terms that failed it rather than read as "nothing matched".
     *
     * @param discardedRelaxed  hits whose term's whole result set came from a relaxed query
     * @param discardedOffTitle hits whose title did not contain the term
     * @param discardedExcluded hits whose title contained an exclusion term
     * @param hotCut            lowest score that read {@code hot} this run (null when nothing scored)
     * @param featuredCut       lowest score that read {@code featured} this run (null likewise)
     */
    public record SeasonRunResult(String seasonKey,
                                  int scanned,
                                  int scored,
                                  int published,
                                  int dropped,
                                  Map<String, Integer> rejections,
                                  int discardedRelaxed,
                                  int discardedOffTitle,
                                  int discardedExcluded,
                                  BigDecimal hotCut,
                                  BigDecimal featuredCut) {
    }

    /** What discovery found and what it threw away. */
    record Discovery(Map<Integer, String> matched, int relaxed, int offTitle, int excluded) {
    }

    /** A candidate that passed the gates, held until the run's tier cuts are known. */
    private record Scored(Integer goodsId,
                          BigDecimal score,
                          BigDecimal marginPct,
                          int stock,
                          boolean euStocked,
                          boolean categoryMatched,
                          long ageDays,
                          String matchedTerm) {
    }

    /** Score every enabled season for today. */
    public List<SeasonRunResult> scoreAll() {
        return scoreAll(LocalDate.now());
    }

    public List<SeasonRunResult> scoreAll(LocalDate day) {
        List<SeasonRunResult> results = new ArrayList<>();
        if (!properties.isEnabled()) {
            log.info("season scoring disabled (litemall.seasons.enabled=false)");
            return results;
        }
        List<LitemallSeasonRule> rules = ruleMapper.selectAll(true);
        if (rules == null || rules.isEmpty()) {
            log.info("season scoring: no enabled season rules");
            return results;
        }
        for (LitemallSeasonRule rule : rules) {
            try {
                results.add(scoreSeason(rule, day));
            } catch (RuntimeException ex) {
                // One bad season must not take the others down with it.
                log.warn("season scoring failed for '{}': {}", rule.getSeasonKey(), ex.toString());
            }
        }
        signalResolver.invalidate();
        return results;
    }

    /** Score one season. Package-visible so tests can drive a single rule. */
    SeasonRunResult scoreSeason(LitemallSeasonRule rule, LocalDate day) {
        SeasonWeights weights = SeasonWeights.parse(rule.getWeights(), objectMapper);
        String hash = configHash(rule);
        String snapshot = weightsSnapshot(weights);
        Set<Integer> boostedCategories = parseIntSet(rule.getCategoryIds());
        SeasonTerms terms = SeasonTerms.of(parseStringList(rule.getTerms()));

        Discovery discovery = discover(rule.getSeasonKey(), terms);
        Map<Integer, String> matched = discovery.matched();
        int scanned = matched.size();
        Map<String, Integer> rejections = new LinkedHashMap<>();

        // Pass 1 — gate and score. Tiers are quantiles of THIS run's curve, so every score has to
        // exist before any tier can be assigned; nothing is written until pass 2.
        List<Scored> scoredRows = new ArrayList<>();
        for (Map.Entry<Integer, String> hit : matched.entrySet()) {
            Integer goodsId = hit.getKey();
            LitemallGoods goods = goodsService.findById(goodsId);
            int stock = stockOf(goodsId);
            BigDecimal cost = goods == null ? null : goods.getCost();

            SeasonCandidateScorer.Rejection rejection = SeasonCandidateScorer.gate(
                    goods, cost, stock,
                    goodsProperties == null ? null : goodsProperties.getPriceFloor(),
                    goodsProperties == null ? null : goodsProperties.getPriceCeiling(),
                    rule.getPriceMin(), rule.getPriceMax());
            if (rejection != SeasonCandidateScorer.Rejection.NONE) {
                rejections.merge(rejection.name(), 1, Integer::sum);
                continue;
            }

            BigDecimal marginPct = marginPct(goods.getRetailPrice(), cost);
            long ageDays = ageDays(goods.getAddTime());
            boolean categoryMatched = goods.getCategoryId() != null
                    && boostedCategories.contains(goods.getCategoryId());

            // A BOOST, never a filter: eu_flag=0 means "not known to hold EU stock", which lumps
            // "probed, none" with "never probed". Excluding on it would quietly hide most of the
            // catalogue; boosting on it just prefers what we can actually deliver quickly.
            boolean euStocked = euStockSignalResolver.euFlag(goods.getCjPid()) == 1;

            BigDecimal score = SeasonCandidateScorer.score(
                    marginPct, stock, goods.getRating(), goods.getReviewCount(),
                    euStocked, categoryMatched, ageDays, weights);
            scoredRows.add(new Scored(goodsId, score, marginPct, stock, euStocked,
                    categoryMatched, ageDays, hit.getValue()));
        }

        List<BigDecimal> scores = new ArrayList<>(scoredRows.size());
        for (Scored row : scoredRows) {
            scores.add(row.score());
        }
        SeasonCandidateScorer.TierCuts cuts = SeasonCandidateScorer.cuts(
                scores, properties.getHotQuantile(), properties.getFeaturedQuantile());

        // Pass 2 — tier against the cuts, decide status, persist.
        int scored = 0;
        int published = 0;
        for (Scored row : scoredRows) {
            Integer goodsId = row.goodsId();
            String tier = SeasonCandidateScorer.tierOf(row.score(), cuts);

            boolean vetoed = candidateMapper.countDismissed(rule.getSeasonKey(), goodsId) > 0;
            String status;
            if (vetoed) {
                // The upsert guard only protects the SAME day's row, so a veto has to be carried
                // forward explicitly or it would quietly expire on the next run.
                status = LitemallSeasonCandidate.STATUS_DISMISSED;
            } else if (properties.isAutoPublishEnabled()
                    && SeasonCandidateScorer.tierReaches(tier, properties.getAutoTier())) {
                status = LitemallSeasonCandidate.STATUS_AUTO;
                published++;
            } else {
                status = LitemallSeasonCandidate.STATUS_PROPOSED;
            }

            LitemallSeasonCandidate candidate = new LitemallSeasonCandidate();
            candidate.setSeasonKey(rule.getSeasonKey());
            candidate.setGoodsId(goodsId);
            candidate.setDay(day);
            candidate.setTier(tier);
            candidate.setScore(row.score());
            candidate.setStatus(status);
            candidate.setConfigVersionHash(hash);
            candidate.setConfigSnapshot(snapshot);
            candidate.setReasons(toJson(SeasonCandidateScorer.reasons(
                    row.marginPct(), row.stock(), row.euStocked(), row.categoryMatched(),
                    row.ageDays(), row.matchedTerm())));
            candidateMapper.upsertProposal(candidate);
            scored++;
        }

        int cap = properties.getPerSeasonCap();
        int dropped = Math.max(0, published - cap);
        log.info("season '{}' {}: scanned {}, scored {}, publishable {} (cap {}{}), rejected {}, "
                        + "tier cuts hot>={} featured>={}, discovery discarded relaxed {} / off-title {} "
                        + "/ excluded {}",
                rule.getSeasonKey(), day, scanned, scored, published, cap,
                dropped > 0 ? ", " + dropped + " beyond the cap will not be shown" : "",
                rejections, cuts.hot(), cuts.featured(),
                discovery.relaxed(), discovery.offTitle(), discovery.excluded());
        return new SeasonRunResult(rule.getSeasonKey(), scanned, scored, published, dropped,
                rejections, discovery.relaxed(), discovery.offTitle(), discovery.excluded(),
                cuts.hot(), cuts.featured());
    }

    /**
     * Candidate discovery: run each discovery term through the index and keep a hit only when the
     * term is IN ITS TITLE and no exclusion term is. The first term that qualifies a product is the
     * one the reason line quotes.
     *
     * <p><b>Why the index alone is not enough.</b> It is tuned for shoppers: it matches descriptions
     * and category names, tolerates typos, and when the exact query finds nothing it falls back to
     * relaxed and n-gram strategies. That is how an "All-Season Sofa Cover" reached the autumn rail
     * through relaxed relevance and a "Summer Cooling Blanket" reached it on the word {@code blanket}.
     * So: a term whose result set the searcher reports as {@code relaxed} contributes NOTHING (the
     * exact query found nothing, so every hit is a guess), every kept hit must carry the term in its
     * title at a word boundary, and exclusion terms ({@code -summer}) veto on the title too.
     *
     * <p>The per-term scan is bounded by {@code candidateScanLimit}, and everything discarded —
     * beyond the limit, relaxed, off-title, excluded — is LOGGED and returned. A silently truncated
     * sweep reads as "we considered everything" when it did not.
     */
    private Discovery discover(String seasonKey, SeasonTerms terms) {
        Map<Integer, String> matched = new LinkedHashMap<>();
        int relaxed = 0;
        int offTitle = 0;
        int excluded = 0;
        int limit = Math.max(1, properties.getCandidateScanLimit());
        for (String term : terms.discoveryTerms()) {
            try {
                Map<String, Object> result =
                        searchService.search(term, 1, limit, null, new HashMap<>());
                List<Map<String, Object>> rows = rowsOf(result);
                Object total = result == null ? null : result.get("total");
                if (total instanceof Number n && n.intValue() > rows.size()) {
                    log.info("season '{}' term '{}': considered {} of {} hits (scan limit {})",
                            seasonKey, term, rows.size(), n.intValue(), limit);
                }
                if (result != null && Boolean.TRUE.equals(result.get("relaxed"))) {
                    relaxed += rows.size();
                    log.info("season '{}' term '{}': {} hits came from a relaxed query ({}) — "
                                    + "the exact term matched nothing, none kept",
                            seasonKey, term, rows.size(), result.get("queryStrategy"));
                    continue;
                }
                int keptForTerm = 0;
                for (Map<String, Object> row : rows) {
                    Integer id = goodsId(row.get("id"));
                    if (id == null) {
                        continue;
                    }
                    String title = row.get("name") instanceof String str ? str : null;
                    if (!SeasonTerms.containsTerm(title, term)) {
                        offTitle++;
                        continue;
                    }
                    if (terms.excludes(title)) {
                        excluded++;
                        continue;
                    }
                    if (matched.putIfAbsent(id, term) == null) {
                        keptForTerm++;
                    }
                }
                log.debug("season '{}' term '{}': kept {} of {} hits", seasonKey, term, keptForTerm,
                        rows.size());
            } catch (RuntimeException ex) {
                // An OCS hiccup costs this term's candidates, not the whole season.
                log.warn("season '{}' term '{}' lookup failed: {}", seasonKey, term, ex.toString());
            }
        }
        if (relaxed + offTitle + excluded > 0) {
            log.info("season '{}' discovery: kept {} products; discarded {} relaxed-query hits, "
                            + "{} off-title hits, {} excluded-term hits",
                    seasonKey, matched.size(), relaxed, offTitle, excluded);
        }
        return new Discovery(matched, relaxed, offTitle, excluded);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rowsOf(Map<String, Object> result) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Object list = result == null ? null : result.get("goodsList");
        if (!(list instanceof List<?> items)) {
            return rows;
        }
        for (Object row : items) {
            if (row instanceof Map<?, ?> map) {
                rows.add((Map<String, Object>) map);
            }
        }
        return rows;
    }

    /**
     * The goods id out of a search hit.
     *
     * <p>⚠ It arrives as a STRING. OCS document ids are strings, and {@code toGoodsListItem} puts
     * {@code document.getId()} straight into the item, so {@code goodsList[].id} is
     * {@code "10010060"} and not {@code 10010060}. Accepting only {@link Number} here silently
     * discarded every hit — the search reported thousands of matches and the scorer scored none.
     * Both forms are accepted so a future change to the item shape cannot re-break this quietly.
     */
    static Integer goodsId(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw instanceof String str && !str.isBlank()) {
            try {
                return Integer.valueOf(str.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private int stockOf(Integer goodsId) {
        List<LitemallGoodsProduct> products = productService.queryByGid(goodsId);
        if (products == null) {
            return 0;
        }
        int total = 0;
        for (LitemallGoodsProduct product : products) {
            if (product.getNumber() != null) {
                total += Math.max(product.getNumber(), 0);
            }
        }
        return total;
    }

    /** Margin as a percentage of retail, or null when cost is absent — never a fabricated 0. */
    static BigDecimal marginPct(BigDecimal retail, BigDecimal cost) {
        if (retail == null || cost == null || retail.signum() <= 0) {
            return null;
        }
        return retail.subtract(cost)
                .divide(retail, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static long ageDays(LocalDateTime addTime) {
        if (addTime == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(addTime.toLocalDate(), LocalDate.now());
    }

    /**
     * Fingerprint of the rule that produced a score. Stored WITH the weights snapshot, never
     * instead of it — a hash alone resolves to nothing once the rule it names has been edited.
     */
    static String configHash(LitemallSeasonRule rule) {
        String material = String.join("|",
                nullSafe(rule.getSeasonKey()), nullSafe(rule.getTerms()),
                nullSafe(rule.getCategoryIds()), nullSafe(rule.getWeights()),
                String.valueOf(rule.getPriceMin()), String.valueOf(rule.getPriceMax()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(material.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "unhashed";
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String weightsSnapshot(SeasonWeights weights) {
        return "{\"euMultiplier\":" + weights.euMultiplier()
                + ",\"freshnessBonusMax\":" + weights.freshnessBonusMax()
                + ",\"categoryBoost\":" + weights.categoryBoost()
                + ",\"demandWeight\":" + weights.demandWeight() + "}";
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<String> parseStringList(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node != null && node.isArray()) {
                node.forEach(n -> {
                    if (n.isTextual() && !n.asText().isBlank()) {
                        out.add(n.asText());
                    }
                });
            }
        } catch (Exception ex) {
            log.warn("unreadable season terms JSON, treating as empty: {}", ex.toString());
        }
        return out;
    }

    private Set<Integer> parseIntSet(String json) {
        Set<Integer> out = new LinkedHashSet<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node != null && node.isArray()) {
                node.forEach(n -> {
                    if (n.isNumber()) {
                        out.add(n.asInt());
                    }
                });
            }
        } catch (Exception ex) {
            log.warn("unreadable season categoryIds JSON, treating as empty: {}", ex.toString());
        }
        return out;
    }
}
