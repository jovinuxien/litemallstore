package org.linlinjava.litemall.goods.domain.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drift guard for the V54 seed templates ("Coupon spotlight", "Group-buy
 * rally"): their config JSON is parsed OUT OF THE MIGRATION FILE and run
 * through the live palette-v1.1 validator, so a validator tightening or a
 * seed edit can never ship a template the editor itself would reject. The
 * migration keeps each config value on a single quote-free line for exactly
 * this parse (see the header comment in V54).
 */
public class PageTemplateSeedTest {

    private static final Path V54 = Path.of("..", "litemall-db", "src", "main", "resources",
            "db", "migration", "V54__page_category_templates.sql");
    /** Wave 27: the season template rides the same drift guard as the V54 pair. */
    private static final Path V63 = Path.of("..", "litemall-db", "src", "main", "resources",
            "db", "migration", "V63__page_season_template.sql");

    private static List<String> seedConfigs() throws Exception {
        return seedConfigs(V54);
    }

    private static List<String> seedConfigs(Path migration) throws Exception {
        List<String> configs = new ArrayList<>();
        for (String line : Files.readAllLines(migration, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("'{\"version\"")) {
                assertThat(trimmed).as("single-line quoted config value").endsWith("',");
                configs.add(trimmed.substring(1, trimmed.length() - 2));
            }
        }
        return configs;
    }

    @Test
    public void bothSeededTemplateConfigsValidateAgainstPaletteV11() throws Exception {
        List<String> configs = seedConfigs();
        assertThat(configs).as("two seeded template configs in V54").hasSize(2);

        PageConfigValidator validator = new PageConfigValidator(new ObjectMapper());
        for (String config : configs) {
            PageConfigValidator.Result result = validator.validate(config);
            assertThat(result.isValid()).as(result.getError()).isTrue();
        }
    }

    @Test
    public void seedsCarryTheirSignatureComponentsWithinTheCaps() throws Exception {
        List<String> configs = seedConfigs();
        ObjectMapper mapper = new ObjectMapper();

        JsonNode coupon = mapper.readTree(configs.get(0));
        JsonNode groupon = mapper.readTree(configs.get(1));
        assertThat(coupon.toString()).contains("coupon-strip");
        assertThat(groupon.toString()).contains("groupon-strip");
        for (JsonNode doc : List.of(coupon, groupon)) {
            assertThat(doc.get("components").size())
                    .isGreaterThan(0)
                    .isLessThanOrEqualTo(PageConfigValidator.MAX_COMPONENTS);
            assertThat(doc.toString().getBytes(StandardCharsets.UTF_8).length)
                    .isLessThanOrEqualTo(PageConfigValidator.MAX_CONFIG_BYTES);
        }
    }

    @Test
    public void theSeasonTemplateConfigValidatesAgainstTheLivePalette() throws Exception {
        List<String> configs = seedConfigs(V63);
        assertThat(configs).as("one seeded template config in V63").hasSize(1);

        PageConfigValidator.Result result =
                new PageConfigValidator(new ObjectMapper()).validate(configs.get(0));
        assertThat(result.isValid()).as(result.getError()).isTrue();
    }

    /**
     * The rail must NOT ship as byIds. The validator demands 1-24 real goods ids for that mode,
     * so a seeded byIds rail could only carry placeholders — which either render nothing or, if
     * the ids happen to exist in the target environment, advertise products no admin chose.
     * Curating it into byIds is a documented admin step, not a migration's job.
     */
    @Test
    public void theSeasonRailIsNotSeededWithPlaceholderProductIds() throws Exception {
        JsonNode config = new ObjectMapper().readTree(seedConfigs(V63).get(0));

        JsonNode rail = null;
        for (JsonNode component : config.get("components")) {
            if ("goods-list".equals(component.get("type").asText())) {
                rail = component;
            }
        }
        assertThat(rail).as("the season template carries a goods rail").isNotNull();
        assertThat(rail.get("config").get("mode").asText()).isNotEqualTo("byIds");
        assertThat(rail.get("config").has("goodsIds")).isFalse();
    }

    @Test
    public void theSeasonSeedIsADraftTemplateSoNothingGoesLiveOnMigration() throws Exception {
        String sql = Files.readString(V63, StandardCharsets.UTF_8);
        assertThat(sql).contains("'Season spotlight', 'custom', 'season'");
        assertThat(sql).contains("'draft', 1, NOW(), NOW()");
        assertThat(sql).doesNotContain("'active'");
    }

    @Test
    public void seedRowsAreDraftCustomTemplatesInTheirCategories() throws Exception {
        String sql = Files.readString(V54, StandardCharsets.UTF_8);
        assertThat(sql).contains("'Coupon spotlight', 'custom', 'coupon'");
        assertThat(sql).contains("'Group-buy rally', 'custom', 'groupon'");
        // status 'draft' + is_template 1 on both seed rows — never active.
        assertThat(sql.split("'draft', 1, NOW\\(\\), NOW\\(\\)", -1)).hasSize(3);
        assertThat(sql).doesNotContain("'active'");
    }
}
