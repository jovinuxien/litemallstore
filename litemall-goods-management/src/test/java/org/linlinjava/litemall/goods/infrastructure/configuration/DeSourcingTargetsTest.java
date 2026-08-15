package org.linlinjava.litemall.goods.infrastructure.configuration;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 26: the shipped DE sourcing targets.
 *
 * <p>The failure this guards is silent. {@code minPrice}/{@code maxPrice} bound CJ's COST in USD,
 * while every other number in this wave is our RETAIL in EUR. Our €25–80 retail band at margin 2.5
 * is €10–32 of cost, which at fx 0.866 is $11.55–$36.95. Someone "correcting" these to 10–32 would
 * source a quietly different band — products that look plausible and price wrong — with nothing
 * failing anywhere.
 */
public class DeSourcingTargetsTest {

    /** €25–80 retail ÷ margin 2.5 ÷ fx 0.866, the band the targets must carry. */
    private static final BigDecimal EXPECTED_MIN = new BigDecimal("11.55");
    private static final BigDecimal EXPECTED_MAX = new BigDecimal("36.95");

    /**
     * Read the SOURCE file, not the classpath. {@code src/test/resources/config/application.yml}
     * is a 0-byte stub, and test-classes precedes classes on the test classpath — so loading this
     * by resource name silently yields an EMPTY document rather than the config under test.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> catalogTargets() throws Exception {
        java.io.File yml = new java.io.File("src/main/resources/config/application.yml");
        assertTrue(yml.isFile() && yml.length() > 0,
                "application.yml not found at " + yml.getAbsolutePath());
        try (InputStream in = new java.io.FileInputStream(yml)) {
            Map<String, Object> root = new Yaml().load(in);
            assertNotNull(root, "application.yml parsed as empty");
            Map<String, Object> spring = (Map<String, Object>) root.get("spring");
            Map<String, Object> cj = (Map<String, Object>) spring.get("cjdropship");
            return (List<Map<String, Object>>) cj.get("catalog-targets");
        }
    }

    private List<Map<String, Object>> deTargets() throws Exception {
        List<Map<String, Object>> de = new ArrayList<>();
        for (Map<String, Object> t : catalogTargets()) {
            if (t.get("country-code") != null) {
                de.add(t);
            }
        }
        return de;
    }

    @Test
    public void deTargetsCarryTheDollarCostBandNotTheEuroRetailOne() throws Exception {
        List<Map<String, Object>> de = deTargets();
        assertFalse(de.isEmpty(), "no DE sourcing targets configured");
        for (Map<String, Object> t : de) {
            BigDecimal min = new BigDecimal(String.valueOf(t.get("min-price")));
            BigDecimal max = new BigDecimal(String.valueOf(t.get("max-price")));
            assertEquals(0, EXPECTED_MIN.compareTo(min),
                    "min-price must be CJ cost in USD ($11.55), not the EUR retail floor: " + t);
            assertEquals(0, EXPECTED_MAX.compareTo(max),
                    "max-price must be CJ cost in USD ($36.95), not the EUR retail ceiling: " + t);
        }
    }

    /** CJ takes ONE country code, max 4 chars — a list is rejected upstream. */
    @Test
    public void everyDeTargetPassesTheClientSideFilterValidation() throws Exception {
        for (Map<String, Object> t : deTargets()) {
            CJDropshippingConfig.CatalogTarget target = new CJDropshippingConfig.CatalogTarget();
            target.setCountryCode(String.valueOf(t.get("country-code")));
            target.setMinPrice(new BigDecimal(String.valueOf(t.get("min-price"))));
            target.setMaxPrice(new BigDecimal(String.valueOf(t.get("max-price"))));
            CjListFilter filter = target.toFilter(); // throws on a comma list or an over-long code
            assertEquals("DE", filter.countryCode(),
                    "DE is the only EU warehouse CJ stocks (Phase 1a: ES/CZ/IT/NL/BE/PL/SE/DK/AT all zero)");
        }
    }

    /**
     * Targets must name LEAVES, not an L1. DE stock is a property of a few hundred individual SKUs
     * — 12 leaves held 308 of the 315 found — so sweeping a whole category spends CJ points on
     * leaves with no DE stock at all.
     */
    @Test
    public void deTargetsAreLeafScopedAndSweepNightly() throws Exception {
        for (Map<String, Object> t : deTargets()) {
            assertNotNull(t.get("category-id"),
                    "a DE target must pin a leaf category-id, not a category name: " + t);
            assertTrue(t.get("category") == null,
                    "category + category-id together is ambiguous: " + t);
            assertTrue((Boolean) t.getOrDefault("nightly", Boolean.TRUE),
                    "DE sourcing is the point of the anchor — it must run nightly: " + t);
        }
    }
}
