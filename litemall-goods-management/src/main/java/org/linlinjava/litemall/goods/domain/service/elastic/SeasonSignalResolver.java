package org.linlinjava.litemall.goods.domain.service.elastic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.linlinjava.litemall.db.dao.LitemallSeasonCandidateMapper;
import org.linlinjava.litemall.db.dao.LitemallSeasonRuleMapper;
import org.linlinjava.litemall.db.domain.LitemallSeasonRule;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSeasonProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The {@code seasons} index field's basis: a memoized snapshot of which goods are published for
 * each season, matched against a product at document-build time. Same shape as
 * {@link CouponSignalResolver} / {@link GrouponSignalResolver} / {@link EuStockSignalResolver} —
 * a short-TTL snapshot, fail-soft on refresh.
 *
 * <p><b>Why this field is multi-valued and the others are 0/1.</b> A product can honestly belong
 * to more than one season at once — a wool throw is both autumn and winter stock — and a page
 * needs to know WHICH season, not merely that some season applies. A boolean would also force a
 * full rescore and reindex every time the year turned, and could never populate the next season's
 * page ahead of its activation. So this returns a list, mirroring {@code category_names}.
 *
 * <p><b>Where the cap is applied.</b> Here, at read time, via
 * {@code selectPublishedGoodsIds(season, cap)}. Capping when a candidate row is written looks more
 * natural and is wrong: rows already at {@code auto} do not re-enter the count, so a second run on
 * the same day publishes past the cap. Ranking the top N by score on read is idempotent however
 * often the scorer runs.
 *
 * <p><b>What an empty list means.</b> "This product is in no season", which is the common case and
 * not an error. Note the deploy consequence recorded in the spec: OCS resolves filter fields
 * against the index MAPPING, which materialises only once a document has held the field, so an
 * index built when NO product had a season may leave {@code seasons} unfilterable. The deploy
 * therefore runs the scorer BEFORE the full reindex rather than emitting a junk sentinel value
 * that would pollute the facet.
 */
@Component
public class SeasonSignalResolver {

    private static final Logger log = LoggerFactory.getLogger(SeasonSignalResolver.class);
    private static final long TTL_MS = 60 * 1000L;

    private final LitemallSeasonRuleMapper ruleMapper;
    private final LitemallSeasonCandidateMapper candidateMapper;
    private final LitemallSeasonProperties properties;

    /** season key → the capped, ranked goods ids published for it. */
    private volatile Map<String, Set<Integer>> snapshot = Map.of();
    private volatile long builtAt = 0L;

    public SeasonSignalResolver(LitemallSeasonRuleMapper ruleMapper,
                                LitemallSeasonCandidateMapper candidateMapper,
                                LitemallSeasonProperties properties) {
        this.ruleMapper = ruleMapper;
        this.candidateMapper = candidateMapper;
        this.properties = properties;
    }

    /**
     * Every season this product is published for, in a stable order, or an empty list.
     *
     * @param goodsId the product; null yields an empty list rather than a throw, because
     *                attribution of any kind is decoration and must never break an index write.
     */
    public List<String> seasonsFor(Integer goodsId) {
        if (goodsId == null || !properties.isEnabled()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, Set<Integer>> entry : membership().entrySet()) {
            if (entry.getValue().contains(goodsId)) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    /** The capped, ranked membership of one season — what a season rail resolves against. */
    public List<Integer> publishedGoodsIds(String seasonKey, int limit) {
        if (seasonKey == null || seasonKey.isBlank() || limit <= 0) {
            return List.of();
        }
        try {
            List<Integer> ids = candidateMapper.selectPublishedGoodsIds(seasonKey, limit);
            return ids == null ? List.of() : ids;
        } catch (RuntimeException ex) {
            // A rail that cannot be resolved renders as absent, never as a broken page.
            log.warn("season membership read failed for '{}': {}", seasonKey, ex.toString());
            return List.of();
        }
    }

    /** Drops the memo so a just-finished scoring run is visible without waiting out the TTL. */
    public void invalidate() {
        builtAt = 0L;
    }

    private Map<String, Set<Integer>> membership() {
        long now = System.currentTimeMillis();
        Map<String, Set<Integer>> snap = snapshot;
        if (now - builtAt < TTL_MS) {
            return snap;
        }
        try {
            List<LitemallSeasonRule> rules = ruleMapper.selectAll(true);
            int cap = properties.getPerSeasonCap();
            Map<String, Set<Integer>> fresh = new LinkedHashMap<>();
            for (LitemallSeasonRule rule : rules == null ? List.<LitemallSeasonRule>of() : rules) {
                List<Integer> ids = candidateMapper.selectPublishedGoodsIds(rule.getSeasonKey(), cap);
                fresh.put(rule.getSeasonKey(),
                        ids == null ? Set.of() : new LinkedHashSet<>(ids));
            }
            snapshot = fresh;
            builtAt = now;
            return fresh;
        } catch (RuntimeException ex) {
            // Fail-soft, exactly as the coupon/groupon/EU resolvers do: the previous snapshot
            // stands and the signal lags, rather than failing the whole index write.
            log.warn("season membership refresh failed, serving the previous snapshot: {}",
                    ex.toString());
            builtAt = now;
            return snap;
        }
    }
}
