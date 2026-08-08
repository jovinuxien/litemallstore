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

    private static List<String> seedConfigs() throws Exception {
        List<String> configs = new ArrayList<>();
        for (String line : Files.readAllLines(V54, StandardCharsets.UTF_8)) {
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
    public void seedRowsAreDraftCustomTemplatesInTheirCategories() throws Exception {
        String sql = Files.readString(V54, StandardCharsets.UTF_8);
        assertThat(sql).contains("'Coupon spotlight', 'custom', 'coupon'");
        assertThat(sql).contains("'Group-buy rally', 'custom', 'groupon'");
        // status 'draft' + is_template 1 on both seed rows — never active.
        assertThat(sql.split("'draft', 1, NOW\\(\\), NOW\\(\\)", -1)).hasSize(3);
        assertThat(sql).doesNotContain("'active'");
    }
}
