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

    /** What one pass did, per season — returned so a manual run reports honestly. */
    public record SeasonRunResult(String seasonKey,
                                  int scanned,
                                  int scored,
                                  int published,
                                  int dropped,
                                  Map<String, Integer> rejections) {
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
        List<String> terms = parseStringList(rule.getTerms());

        Map<Integer, String> matched = discover(rule.getSeasonKey(), terms);
        int scanned = matched.size();
        int scored = 0;
        int published = 0;
        Map<String, Integer> rejections = new LinkedHashMap<>();

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
            String tier = SeasonCandidateScorer.tierOf(score);

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

            LitemallSeasonCandidate row = new LitemallSeasonCandidate();
            row.setSeasonKey(rule.getSeasonKey());
            row.setGoodsId(goodsId);
            row.setDay(day);
            row.setTier(tier);
            row.setScore(score);
            row.setStatus(status);
            row.setConfigVersionHash(hash);
            row.setConfigSnapshot(snapshot);
            row.setReasons(toJson(SeasonCandidateScorer.reasons(
                    marginPct, stock, euStocked, categoryMatched, ageDays, hit.getValue())));
            candidateMapper.upsertProposal(row);
            scored++;
        }

        int cap = properties.getPerSeasonCap();
        int dropped = Math.max(0, published - cap);
        log.info("season '{}' {}: scanned {}, scored {}, publishable {} (cap {}{}), rejected {}",
                rule.getSeasonKey(), day, scanned, scored, published, cap,
                dropped > 0 ? ", " + dropped + " beyond the cap will not be shown" : "",
                rejections);
        return new SeasonRunResult(rule.getSeasonKey(), scanned, scored, published, dropped, rejections);
    }

    /**
     * Candidate discovery: run each term through the index, keep the first term that matched a
     * product (that is what the reason line quotes).
     *
     * <p>The per-term scan is bounded by {@code candidateScanLimit} and whatever it drops is
     * LOGGED — a silently truncated sweep reads as "we considered everything" when it did not.
     */
    private Map<Integer, String> discover(String seasonKey, List<String> terms) {
        Map<Integer, String> matched = new LinkedHashMap<>();
        int limit = Math.max(1, properties.getCandidateScanLimit());
        for (String term : terms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            try {
                Map<String, Object> result =
                        searchService.search(term, 1, limit, null, new HashMap<>());
                List<Integer> ids = goodsIdsOf(result);
                Object total = result == null ? null : result.get("total");
                if (total instanceof Number n && n.intValue() > ids.size()) {
                    log.info("season '{}' term '{}': considered {} of {} hits (scan limit {})",
                            seasonKey, term, ids.size(), n.intValue(), limit);
                }
                for (Integer id : ids) {
                    matched.putIfAbsent(id, term);
                }
            } catch (RuntimeException ex) {
                // An OCS hiccup costs this term's candidates, not the whole season.
                log.warn("season '{}' term '{}' lookup failed: {}", seasonKey, term, ex.toString());
            }
        }
        return matched;
    }

    @SuppressWarnings("unchecked")
    private List<Integer> goodsIdsOf(Map<String, Object> result) {
        List<Integer> ids = new ArrayList<>();
        Object list = result == null ? null : result.get("goodsList");
        if (!(list instanceof List<?> rows)) {
            return ids;
        }
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map) {
                Integer id = goodsId(((Map<String, Object>) map).get("id"));
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
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
