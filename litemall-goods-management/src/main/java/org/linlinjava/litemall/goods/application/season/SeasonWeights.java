package org.linlinjava.litemall.goods.application.season;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * One season's tunable weights, parsed from {@code litemall_season_rule.weights}.
 *
 * <p>These are the ONLY things that vary between seasons. The global gates — profitability,
 * availability, price band — are identical everywhere, because a season may decide what it wants,
 * never whether the store can afford to sell it. That split is what lets a new season be described
 * purely in configuration, with no code edit.
 *
 * <p>Every value is clamped to a sane range on parse. The score is multiplicative, so a weight of
 * 0 in the database would silently zero every product in that season; clamping means a bad edit
 * degrades the ranking instead of emptying the page.
 */
public record SeasonWeights(double euMultiplier,
                            double freshnessBonusMax,
                            double categoryBoost,
                            double demandWeight) {

    public static final SeasonWeights DEFAULTS = new SeasonWeights(1.20, 1.25, 1.15, 1.00);

    /** Multipliers below this are treated as a mistake, not an intention. */
    private static final double MIN_MULTIPLIER = 1.0;
    private static final double MAX_MULTIPLIER = 3.0;

    /**
     * Parse the weights JSON, falling back to {@link #DEFAULTS} for anything missing or unusable.
     * Never throws: a malformed weights blob must degrade a season's ranking, not break scoring
     * for every other season in the same run.
     */
    public static SeasonWeights parse(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank() || mapper == null) {
            return DEFAULTS;
        }
        try {
            JsonNode node = mapper.readTree(json);
            if (node == null || !node.isObject()) {
                return DEFAULTS;
            }
            return new SeasonWeights(
                    clamp(node.path("euMultiplier").asDouble(DEFAULTS.euMultiplier())),
                    clamp(node.path("freshnessBonusMax").asDouble(DEFAULTS.freshnessBonusMax())),
                    clamp(node.path("categoryBoost").asDouble(DEFAULTS.categoryBoost())),
                    clamp(node.path("demandWeight").asDouble(DEFAULTS.demandWeight())));
        } catch (Exception ex) {
            return DEFAULTS;
        }
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return MIN_MULTIPLIER;
        }
        return Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, value));
    }
}
