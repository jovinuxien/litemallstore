package org.linlinjava.litemall.goods.domain.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Palette validation, v1 core rules + the v1.1 additions (Wave 20):
 * the new {@code groupon-strip} component and the {@code coupon-strip}
 * extensions ({@code couponIds}/{@code headline}/{@code style}). v1.1 is
 * strictly additive — every valid v1 document must keep validating.
 * Normative contract: {@code docs/spec-page-palette-v1.md} §2 + §8.
 */
public class PageConfigValidatorTest {

    private final PageConfigValidator validator = new PageConfigValidator(new ObjectMapper());

    private static String doc(String components) {
        return "{\"version\":1,\"components\":[" + components + "]}";
    }

    private PageConfigValidator.Result validate(String components) {
        return validator.validate(doc(components));
    }

    // ---------------- v1 backward compatibility ----------------

    @Test
    public void plainV1ConfigStaysValid() {
        PageConfigValidator.Result result = validate(
                "{\"type\":\"banner\",\"config\":{\"image\":\"/_cdn/x.jpg\",\"link\":\"/deals\"}},"
                + "{\"type\":\"coupon-strip\",\"config\":{\"limit\":3,\"title\":\"Coupons\"}},"
                + "{\"type\":\"seckill-strip\",\"config\":{}},"
                + "{\"type\":\"goods-list\",\"config\":{\"mode\":\"deals\",\"limit\":8}},"
                + "{\"type\":\"rich-text\",\"config\":{\"html\":\"<p>hello</p>\"}}");
        assertThat(result.isValid()).as(result.getError()).isTrue();
    }

    @Test
    public void bareCouponStripWithoutAnyConfigStaysValid() {
        // v1 shape: strips are fully defaulted — no v1.1 field may become required.
        PageConfigValidator.Result result = validate("{\"type\":\"coupon-strip\"}");
        assertThat(result.isValid()).as(result.getError()).isTrue();
    }

    @Test
    public void unknownComponentTypeStillNamesTheComponent() {
        PageConfigValidator.Result result = validate("{\"type\":\"mystery-strip\",\"config\":{}}");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getError()).contains("component[0] (mystery-strip)");
    }

    @Test
    public void wrongVersionIsRejected() {
        PageConfigValidator.Result result = validator.validate("{\"version\":2,\"components\":[]}");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getError()).contains("version");
    }

    // ---------------- v1.1 coupon-strip extensions ----------------

    @Test
    public void couponStripAcceptsAllV11Fields() {
        PageConfigValidator.Result result = validate(
                "{\"type\":\"coupon-strip\",\"config\":{\"couponIds\":[3,7],"
                + "\"headline\":\"Claim yours\",\"style\":\"grid\",\"limit\":6}}");
        assertThat(result.isValid()).as(result.getError()).isTrue();
    }

    @Test
    public void couponStripEmptyCouponIdsMeansAutoAndIsValid() {
        PageConfigValidator.Result result = validate(
                "{\"type\":\"coupon-strip\",\"config\":{\"couponIds\":[]}}");
        assertThat(result.isValid()).as(result.getError()).isTrue();
    }

    @Test
    public void couponStripRejectsNonPositiveCouponId() {
        PageConfigValidator.Result result = validate(
                "{\"type\":\"coupon-strip\",\"config\":{\"couponIds\":[3,0]}}");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getError()).contains("coupon-strip").contains("couponIds[1]");
    }

    @Test
    public void couponStripRejectsNonArrayCouponIds() {
        PageConfigValidator.Result result = validate(
                "{\"type\":\"coupon-strip\",\"config\":{\"couponIds\":\"3,7\"}}");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getError()).contains("couponIds must be an array");
    }

    @Test
    public void couponStripRejectsUnknownStyle() {
        PageConfigValidator.Result result = validate(
                "{\"type\":\"coupon-strip\",\"config\":{\"style\":\"carousel\"}}");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getError()).contains("style must be one of");
    }

    @Test
    public void couponStripRejectsNonStringHeadline() {
        PageConfigValidator.Result result = validate(
                "{\"type\":\"coupon-strip\",\"config\":{\"headline\":42}}");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getError()).contains("headline must be a string");
    }

    // ---------------- v1.1 groupon-strip ----------------

    @Test
    public void grouponStripDefaultsAreValid() {
        PageConfigValidator.Result result = validate("{\"type\":\"groupon-strip\",\"config\":{}}");
        assertThat(result.isValid()).as(result.getError()).isTrue();
    }

    @Test
    public void grouponStripAcceptsExplicitIdsAndBounds() {
        PageConfigValidator.Result result = validate(
                "{\"type\":\"groupon-strip\",\"config\":{\"title\":\"Rallies\","
                + "\"combinationIds\":[11,12,13],\"maxItems\":12}}");
        assertThat(result.isValid()).as(result.getError()).isTrue();
    }

    @Test
    public void grouponStripRejectsMaxItemsOutOfRange() {
        assertThat(validate("{\"type\":\"groupon-strip\",\"config\":{\"maxItems\":0}}").isValid()).isFalse();
        PageConfigValidator.Result result =
                validate("{\"type\":\"groupon-strip\",\"config\":{\"maxItems\":13}}");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getError()).contains("maxItems must be an integer between 1 and 12");
    }

    @Test
    public void grouponStripRejectsBadCombinationIdEntry() {
        PageConfigValidator.Result result = validate(
                "{\"type\":\"groupon-strip\",\"config\":{\"combinationIds\":[5,\"x\"]}}");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getError()).contains("combinationIds[1] must be a positive integer");
    }

    @Test
    public void grouponStripRejectsNonStringTitle() {
        PageConfigValidator.Result result = validate(
                "{\"type\":\"groupon-strip\",\"config\":{\"title\":[]}}");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getError()).contains("groupon-strip").contains("title must be a string");
    }

    // ---------------- schema ----------------

    @Test
    public void paletteSchemaCarriesTheV11Components() {
        String schema;
        try {
            schema = new ObjectMapper().writeValueAsString(PageConfigValidator.paletteSchema());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertThat(schema)
                .contains("\"revision\":\"1.1\"")
                .contains("groupon-strip")
                .contains("combinationIds")
                .contains("maxItems")
                .contains("couponIds")
                .contains("headline");
    }
}
