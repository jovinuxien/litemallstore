package org.linlinjava.litemall.goods.domain.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Palette-v1 validator — PURE domain service (no Spring, no IO). The single
 * source of truth for the component catalog lives here next to
 * {@link #paletteSchema()} so the machine-readable schema served at
 * {@code GET /srv/private/admin/page/palette} can never drift from what is
 * enforced. Normative contract: {@code docs/spec-page-palette-v1.md}.
 *
 * <p>Every violation is reported with the offending component named
 * ({@code component[i] (type): reason}) — errno 640 at the edge. The one
 * non-rejecting rule: {@code rich-text.html} is SANITIZED in place
 * (clean-and-store, {@link HtmlSanitizer}) rather than refused.
 */
public final class PageConfigValidator {

    public static final int VERSION = 1;
    public static final int MAX_COMPONENTS = 30;
    public static final int MAX_CONFIG_BYTES = 65536;

    // "deals" (palette v1.1): renderer resolves /srv/search?deal_flag=1 — discounted goods,
    // deepest-and-most-popular first via the searcher's discount_pct scoring. Same degrade
    // rule R1 as every strip: no resolvable data ⇒ skip, never an error.
    private static final Set<String> GOODS_LIST_MODES = Set.of("byIds", "byCategory", "hot", "new", "deals");
    // "groupon-strip" (palette v1.1, Wave 20): renderer resolves
    // /srv/promotion/combination/active. Degrade rule R1 as every strip.
    private static final Set<String> KNOWN_TYPES = Set.of(
            "banner", "image-row", "goods-list", "coupon-strip", "seckill-strip",
            "groupon-strip", "article-strip", "rich-text");
    private static final Set<String> COUPON_STRIP_STYLES = Set.of("strip", "grid");

    private final ObjectMapper mapper;

    public PageConfigValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Outcome: either {@code error != null}, or {@code normalizedJson} is safe to store. */
    public static final class Result {
        private final String error;
        private final String normalizedJson;

        private Result(String error, String normalizedJson) {
            this.error = error;
            this.normalizedJson = normalizedJson;
        }

        public boolean isValid() {
            return error == null;
        }

        public String getError() {
            return error;
        }

        public String getNormalizedJson() {
            return normalizedJson;
        }
    }

    private static Result fail(String message) {
        return new Result(message, null);
    }

    /**
     * Validate a raw palette-v1 JSON document. On success the returned
     * {@link Result#getNormalizedJson()} carries the re-serialized document
     * with rich-text sanitized in place — persist THAT, not the input.
     */
    public Result validate(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return fail("config is required");
        }
        JsonNode root;
        try {
            root = mapper.readTree(rawJson);
        } catch (Exception e) {
            return fail("config is not valid JSON: " + e.getMessage());
        }
        if (!root.isObject()) {
            return fail("config must be a JSON object");
        }
        JsonNode version = root.get("version");
        if (version == null || !version.isInt() || version.asInt() != VERSION) {
            return fail("config.version must be " + VERSION);
        }
        JsonNode components = root.get("components");
        if (components == null || !components.isArray()) {
            return fail("config.components must be an array");
        }
        if (components.size() > MAX_COMPONENTS) {
            return fail("config.components has " + components.size()
                    + " entries; the maximum is " + MAX_COMPONENTS);
        }

        ArrayNode componentArray = (ArrayNode) components;
        for (int i = 0; i < componentArray.size(); i++) {
            JsonNode entry = componentArray.get(i);
            if (!entry.isObject()) {
                return fail("component[" + i + "]: must be a JSON object");
            }
            JsonNode typeNode = entry.get("type");
            if (typeNode == null || !typeNode.isTextual() || typeNode.asText().isBlank()) {
                return fail("component[" + i + "]: missing type");
            }
            String type = typeNode.asText();
            if (!KNOWN_TYPES.contains(type)) {
                return fail("component[" + i + "] (" + type + "): unknown component type");
            }
            JsonNode keyNode = entry.get("key");
            if (keyNode != null && !keyNode.isNull() && !keyNode.isTextual()) {
                return fail(prefix(i, type) + "key must be a string");
            }

            JsonNode configNode = entry.get("config");
            if (configNode == null || configNode.isNull()) {
                // Strips are fully defaulted; normalize an explicit empty object in.
                configNode = ((ObjectNode) entry).putObject("config");
            }
            if (!configNode.isObject()) {
                return fail(prefix(i, type) + "config must be a JSON object");
            }
            ObjectNode config = (ObjectNode) configNode;

            String error = switch (type) {
                case "banner" -> validateBanner(i, config);
                case "image-row" -> validateImageRow(i, config);
                case "goods-list" -> validateGoodsList(i, config);
                case "coupon-strip" -> validateCouponStrip(i, config);
                case "seckill-strip" -> validateStrip(i, "seckill-strip", config);
                case "groupon-strip" -> validateGrouponStrip(i, config);
                case "article-strip" -> validateArticleStrip(i, config);
                case "rich-text" -> validateAndSanitizeRichText(i, config);
                default -> null; // unreachable — KNOWN_TYPES gate above
            };
            if (error != null) {
                return fail(error);
            }
        }

        String normalized;
        try {
            normalized = mapper.writeValueAsString(root);
        } catch (Exception e) {
            return fail("config could not be re-serialized: " + e.getMessage());
        }
        int bytes = normalized.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_CONFIG_BYTES) {
            return fail("config is " + bytes + " bytes; the maximum is " + MAX_CONFIG_BYTES);
        }
        return new Result(null, normalized);
    }

    private static String prefix(int idx, String type) {
        return "component[" + idx + "] (" + type + "): ";
    }

    private static String validateBanner(int i, ObjectNode config) {
        String err = requireString(i, "banner", config, "image");
        if (err != null) {
            return err;
        }
        err = optionalString(i, "banner", config, "link");
        if (err != null) {
            return err;
        }
        return optionalString(i, "banner", config, "title");
    }

    private static String validateImageRow(int i, ObjectNode config) {
        JsonNode images = config.get("images");
        if (images == null || !images.isArray() || images.isEmpty()) {
            return prefix(i, "image-row") + "images requires 1-8 entries";
        }
        if (images.size() > 8) {
            return prefix(i, "image-row") + "images has " + images.size() + " entries; the maximum is 8";
        }
        for (int j = 0; j < images.size(); j++) {
            JsonNode item = images.get(j);
            if (!item.isObject()) {
                return prefix(i, "image-row") + "images[" + j + "] must be an object";
            }
            JsonNode image = item.get("image");
            if (image == null || !image.isTextual() || image.asText().isBlank()) {
                return prefix(i, "image-row") + "images[" + j + "].image is required";
            }
            JsonNode link = item.get("link");
            if (link != null && !link.isNull() && !link.isTextual()) {
                return prefix(i, "image-row") + "images[" + j + "].link must be a string";
            }
        }
        return null;
    }

    private static String validateGoodsList(int i, ObjectNode config) {
        JsonNode modeNode = config.get("mode");
        if (modeNode == null || !modeNode.isTextual() || !GOODS_LIST_MODES.contains(modeNode.asText())) {
            return prefix(i, "goods-list") + "mode must be one of " + GOODS_LIST_MODES;
        }
        String mode = modeNode.asText();
        if ("byIds".equals(mode)) {
            JsonNode ids = config.get("goodsIds");
            if (ids == null || !ids.isArray() || ids.isEmpty()) {
                return prefix(i, "goods-list") + "mode=byIds requires goodsIds (1-24 ids)";
            }
            if (ids.size() > 24) {
                return prefix(i, "goods-list") + "goodsIds has " + ids.size() + " entries; the maximum is 24";
            }
            for (int j = 0; j < ids.size(); j++) {
                JsonNode id = ids.get(j);
                if (!id.isIntegralNumber() || id.asLong() <= 0) {
                    return prefix(i, "goods-list") + "goodsIds[" + j + "] must be a positive integer";
                }
            }
        }
        if ("byCategory".equals(mode)) {
            JsonNode categoryId = config.get("categoryId");
            if (categoryId == null || !categoryId.isIntegralNumber() || categoryId.asLong() <= 0) {
                return prefix(i, "goods-list") + "mode=byCategory requires a positive integer categoryId";
            }
        }
        String err = optionalBoundedInt(i, "goods-list", config, "limit", 1, 24);
        if (err != null) {
            return err;
        }
        return optionalString(i, "goods-list", config, "title");
    }

    private static String validateStrip(int i, String type, ObjectNode config) {
        String err = optionalBoundedInt(i, type, config, "limit", 1, 10);
        if (err != null) {
            return err;
        }
        return optionalString(i, type, config, "title");
    }

    /** v1.1: the v1 strip fields plus optional couponIds / headline / style. */
    private static String validateCouponStrip(int i, ObjectNode config) {
        String err = validateStrip(i, "coupon-strip", config);
        if (err != null) {
            return err;
        }
        err = optionalIdArray(i, "coupon-strip", config, "couponIds");
        if (err != null) {
            return err;
        }
        err = optionalString(i, "coupon-strip", config, "headline");
        if (err != null) {
            return err;
        }
        JsonNode style = config.get("style");
        if (style != null && !style.isNull()
                && (!style.isTextual() || !COUPON_STRIP_STYLES.contains(style.asText()))) {
            return prefix(i, "coupon-strip") + "style must be one of " + COUPON_STRIP_STYLES;
        }
        return null;
    }

    /** v1.1: explicit combinationIds or auto active campaigns; maxItems 1-12 (default 4). */
    private static String validateGrouponStrip(int i, ObjectNode config) {
        String err = optionalString(i, "groupon-strip", config, "title");
        if (err != null) {
            return err;
        }
        err = optionalIdArray(i, "groupon-strip", config, "combinationIds");
        if (err != null) {
            return err;
        }
        return optionalBoundedInt(i, "groupon-strip", config, "maxItems", 1, 12);
    }

    private static String validateArticleStrip(int i, ObjectNode config) {
        String err = validateStrip(i, "article-strip", config);
        if (err != null) {
            return err;
        }
        JsonNode hotOnly = config.get("hotOnly");
        if (hotOnly != null && !hotOnly.isNull() && !hotOnly.isBoolean()) {
            return prefix(i, "article-strip") + "hotOnly must be a boolean";
        }
        return null;
    }

    /** The only mutating rule: html is cleaned in place, never rejected for markup. */
    private static String validateAndSanitizeRichText(int i, ObjectNode config) {
        JsonNode html = config.get("html");
        if (html == null || !html.isTextual() || html.asText().isBlank()) {
            return prefix(i, "rich-text") + "html is required";
        }
        config.put("html", HtmlSanitizer.sanitize(html.asText()));
        return null;
    }

    private static String requireString(int i, String type, ObjectNode config, String field) {
        JsonNode node = config.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            return prefix(i, type) + field + " is required";
        }
        return null;
    }

    private static String optionalString(int i, String type, ObjectNode config, String field) {
        JsonNode node = config.get(field);
        if (node != null && !node.isNull() && !node.isTextual()) {
            return prefix(i, type) + field + " must be a string";
        }
        return null;
    }

    /**
     * Optional array of positive integer ids. Absent, null and EMPTY all pass —
     * for the v1.1 strips an empty/absent id list means "automatic" (renderer
     * shows whatever is live). The 64KB document cap bounds the length.
     */
    private static String optionalIdArray(int i, String type, ObjectNode config, String field) {
        JsonNode node = config.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isArray()) {
            return prefix(i, type) + field + " must be an array of positive integers";
        }
        for (int j = 0; j < node.size(); j++) {
            JsonNode id = node.get(j);
            if (!id.isIntegralNumber() || id.asLong() <= 0) {
                return prefix(i, type) + field + "[" + j + "] must be a positive integer";
            }
        }
        return null;
    }

    private static String optionalBoundedInt(int i, String type, ObjectNode config,
                                             String field, int min, int max) {
        JsonNode node = config.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber() || node.asInt() < min || node.asInt() > max) {
            return prefix(i, type) + field + " must be an integer between " + min + " and " + max;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Machine-readable schema (GET /srv/private/admin/page/palette) — the
    // structured admin editor is built from THIS, not hardcoded forms.
    // ------------------------------------------------------------------

    /** Immutable schema document describing exactly what {@link #validate} enforces. */
    public static Map<String, Object> paletteSchema() {
        return Map.of(
                "version", VERSION,
                "revision", "1.1",
                "maxComponents", MAX_COMPONENTS,
                "maxConfigBytes", MAX_CONFIG_BYTES,
                "degradeRule", "R1: a component with no resolvable data is skipped by the renderer, never an error",
                "components", List.of(
                        component("banner", "Hero banner (static)", List.of(
                                field("image", "string", true, Map.of("description", "image URL")),
                                field("link", "string", false, Map.of("description", "SPA route or absolute URL")),
                                field("title", "string", false, Map.of()))),
                        component("image-row", "Row of linked images (static)", List.of(
                                field("images", "array", true, Map.of(
                                        "minItems", 1, "maxItems", 8,
                                        "itemFields", List.of(
                                                field("image", "string", true, Map.of()),
                                                field("link", "string", false, Map.of())))))),
                        component("goods-list", "Goods rail (byIds|byCategory|hot|new|deals)", List.of(
                                field("mode", "enum", true, Map.of("values", List.of("byIds", "byCategory", "hot", "new", "deals"))),
                                field("goodsIds", "int[]", false, Map.of(
                                        "requiredWhen", "mode=byIds", "minItems", 1, "maxItems", 24)),
                                field("categoryId", "int", false, Map.of("requiredWhen", "mode=byCategory")),
                                field("limit", "int", false, Map.of("min", 1, "max", 24, "default", 8,
                                        "description", "ignored for byIds")),
                                field("title", "string", false, Map.of()))),
                        component("coupon-strip", "Claimable coupons (renderer slices — endpoint has no limit param)", List.of(
                                field("limit", "int", false, Map.of("min", 1, "max", 10, "default", 3)),
                                field("title", "string", false, Map.of()),
                                field("couponIds", "int[]", false, Map.of(
                                        "description", "explicit coupon ids; empty/absent = automatic available coupons")),
                                field("headline", "string", false, Map.of(
                                        "description", "large merchandising headline above the strip")),
                                field("style", "enum", false, Map.of(
                                        "values", List.of("strip", "grid"), "default", "strip")))),
                        component("seckill-strip", "Active seckills (renderer slices — endpoint has no limit param)", List.of(
                                field("limit", "int", false, Map.of("min", 1, "max", 10, "default", 3)),
                                field("title", "string", false, Map.of()))),
                        component("groupon-strip", "Active group-buy campaigns (renderer slices)", List.of(
                                field("title", "string", false, Map.of()),
                                field("combinationIds", "int[]", false, Map.of(
                                        "description", "explicit combination ids; empty/absent = automatic active campaigns")),
                                field("maxItems", "int", false, Map.of("min", 1, "max", 12, "default", 4)))),
                        component("article-strip", "Latest/hot articles", List.of(
                                field("limit", "int", false, Map.of("min", 1, "max", 10, "default", 3)),
                                field("hotOnly", "boolean", false, Map.of("default", false)),
                                field("title", "string", false, Map.of()))),
                        component("rich-text", "Sanitized HTML block (clean-and-store)", List.of(
                                field("html", "string", true, Map.of("sanitized", true))))));
    }

    private static Map<String, Object> component(String type, String label, List<Map<String, Object>> fields) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("label", label);
        m.put("fields", fields);
        return m;
    }

    private static Map<String, Object> field(String name, String type, boolean required, Map<String, Object> extra) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("type", type);
        m.put("required", required);
        m.putAll(extra);
        return m;
    }
}
