package org.linlinjava.litemall.goods.application.season;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Weights are edited at runtime by an admin, so a bad edit must degrade a season's ranking rather
 * than empty its page — the score is multiplicative, and a 0 in the database would silently zero
 * every product in that season.
 */
public class SeasonWeightsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void realWeightsParse() {
        SeasonWeights w = SeasonWeights.parse(
                "{\"euMultiplier\":1.4,\"freshnessBonusMax\":1.25,"
                        + "\"categoryBoost\":1.15,\"demandWeight\":1.0}", mapper);
        assertEquals(1.4, w.euMultiplier(), 0.0001);
        assertEquals(1.25, w.freshnessBonusMax(), 0.0001);
    }

    @Test
    public void aZeroWeightIsClampedSoItCannotZeroASeason() {
        SeasonWeights w = SeasonWeights.parse("{\"euMultiplier\":0,\"demandWeight\":0}", mapper);
        assertEquals(1.0, w.euMultiplier(), 0.0001);
        assertEquals(1.0, w.demandWeight(), 0.0001);
    }

    @Test
    public void anAbsurdWeightIsCapped() {
        assertEquals(3.0, SeasonWeights.parse("{\"categoryBoost\":99}", mapper).categoryBoost(),
                0.0001);
    }

    @Test
    public void missingKeysFallBackToDefaultsRatherThanZero() {
        SeasonWeights w = SeasonWeights.parse("{}", mapper);
        assertEquals(SeasonWeights.DEFAULTS, w);
    }

    /** A malformed blob must not break scoring for every OTHER season in the same run. */
    @Test
    public void malformedJsonDegradesToDefaults() {
        assertEquals(SeasonWeights.DEFAULTS, SeasonWeights.parse("{not json", mapper));
        assertEquals(SeasonWeights.DEFAULTS, SeasonWeights.parse(null, mapper));
        assertEquals(SeasonWeights.DEFAULTS, SeasonWeights.parse("[1,2,3]", mapper));
    }
}
