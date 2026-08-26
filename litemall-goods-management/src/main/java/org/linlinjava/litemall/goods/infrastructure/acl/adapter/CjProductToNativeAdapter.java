package org.linlinjava.litemall.goods.infrastructure.acl.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsAttribute;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.db.domain.LitemallGoodsSpecification;
import org.linlinjava.litemall.goods.application.search.SupplierTitle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Anti-corruption adapter: maps an enriched {@code litemall_cj_product} landing-zone row
 * onto a {@link NativeGoodsAggregate} of native litemall domain objects.
 *
 * <p>It reads only the row's already-enriched JSON columns ({@code variants_json},
 * {@code attributes_json}, {@code images_json}, {@code category_*}) — no CJ API call —
 * so it never spends the rate-limited budget. Pricing is taken verbatim from the row,
 * which the sync/enrichment steps already marked up ({@code wholesale_usd × margin}); the raw
 * wholesale cost rides along (Wave 12): {@code row.sell_price} → {@code goods.cost},
 * {@code variant_sell_price} → {@code goods_product.cost}. A null cost (row not re-synced
 * since V45) is preserved as "not captured" by the mappers' COALESCE semantics.
 *
 * <p>Identity is preserved end-to-end: CJ {@code pid} → {@link LitemallGoods#getCjPid()},
 * each variant {@code vid} → {@link LitemallGoodsProduct#getCjVid()}.
 */
@Component
public class CjProductToNativeAdapter {

    private static final Logger log = LoggerFactory.getLogger(CjProductToNativeAdapter.class);

    /** Source discriminator written to {@code litemall_goods.source} / {@code litemall_*.source}. */
    public static final String SOURCE_CJ = "cj";

    private static final String GOODS_SN_PREFIX = "cj_";
    // Wave 25 hygiene: no fake unit. The old default stamped the Chinese glyph "件" on every
    // promoted CJ good lacking a Unit attribute, and it rendered beside the € price on the PDP.
    private static final String DEFAULT_UNIT = "";
    private static final String SPEC_AXIS = "Specification";
    private static final String DEFAULT_SPEC_VALUE = "Standard";
    private static final String UNIT_ATTRIBUTE = "Unit";

    // litemall_goods column widths we must respect (the rest are text / unbounded).
    private static final int NAME_MAX = 127;
    private static final int VARCHAR_MAX = 255;
    private static final int UNIT_MAX = 31;
    // litemall_goods.gallery is TEXT (widened in V23); keep the serialized JSON array well under
    // the ~64KB TEXT ceiling as a guard, while preserving the full CJ gallery in practice.
    private static final int GALLERY_JSON_BUDGET = 60000;

    // litemall_goods_product.url is varchar(125); a variant image that doesn't fit falls back to
    // the main photo — truncating a URL yields a broken image, not a shorter one.
    private static final int PRODUCT_URL_MAX = 125;
    // Hosts the /_cdn edge rewrite covers (CjImageUrlRewriteFilter + Caddyfile /_cdn/cf|oss/*).
    // A variant image on any other host would serve mixed-content/unproxied on the storefront,
    // so it is treated as absent (main-photo fallback).
    private static final String[] CDN_COVERED_IMAGE_PREFIXES = {
            "https://cf.cjdropshipping.com/",
            "http://cf.cjdropshipping.com/",
            "https://oss-cf.cjdropshipping.com/",
            "http://oss-cf.cjdropshipping.com/",
    };

    private final ObjectMapper objectMapper;

    public CjProductToNativeAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NativeGoodsAggregate adapt(LitemallCjProduct row) {
        Objects.requireNonNull(row, "cj product row");
        final String pid = row.getPid();
        final LocalDateTime now = LocalDateTime.now();
        final BigDecimal retail = row.getPrice() != null ? row.getPrice() : BigDecimal.ZERO;

        LitemallGoods goods = new LitemallGoods();
        goods.setSource(SOURCE_CJ);
        goods.setCjPid(pid);
        goods.setGoodsSn(GOODS_SN_PREFIX + pid);
        // Supplier bookkeeping is stripped HERE, where the customer-facing name is derived,
        // not in a cleanup pass over litemall_goods. Cleaning the goods row alone is undone by
        // the very next promote — which is what happened on 2026-08-26, when a full promote run
        // for an unrelated price change silently restored all 68 prefixes an hour after they
        // were removed.
        goods.setName(trim(SupplierTitle.strip(row.getTitle()), NAME_MAX));
        // keywords keeps the RAW title on purpose: it is the search column
        // (LitemallGoodsService.querySelective LIKE-matches it), so a shopper who searches the
        // untrimmed supplier string still finds the product.
        goods.setKeywords(trim(row.getTitle(), VARCHAR_MAX));
        goods.setBrief(trim(row.getDescription(), VARCHAR_MAX));
        goods.setDetail(row.getDetailHtml());
        goods.setPicUrl(trim(row.getImageUrl(), VARCHAR_MAX));
        goods.setGallery(boundedGallery(parseStringList(row.getImagesJson())));
        goods.setRetailPrice(retail);
        // CJ exposes no strike-through price, so counter == retail (no implied discount).
        goods.setCounterPrice(retail);
        goods.setCost(row.getSellPrice());
        goods.setIsOnSale(Boolean.TRUE);
        goods.setIsNew(Boolean.FALSE);
        goods.setIsHot(Boolean.FALSE);
        goods.setSortOrder((short) 100);
        goods.setShareUrl("");
        goods.setDeleted(Boolean.FALSE);
        goods.setAddTime(row.getAddTime() != null ? row.getAddTime() : now);
        goods.setUpdateTime(now);

        NativeGoodsAggregate aggregate = new NativeGoodsAggregate(goods);

        // ---- attributes: flat {name -> value} -> litemall_goods_attribute rows ----
        Map<String, String> attrs = parseFlatMap(row.getAttributesJson());
        goods.setUnit(normalizeUnit(attrs.get(UNIT_ATTRIBUTE)));
        for (Map.Entry<String, String> e : attrs.entrySet()) {
            if (!StringUtils.hasText(e.getKey()) || !StringUtils.hasText(e.getValue())) {
                continue;
            }
            LitemallGoodsAttribute attribute = new LitemallGoodsAttribute();
            attribute.setAttribute(trim(e.getKey(), VARCHAR_MAX));
            attribute.setValue(trim(e.getValue(), VARCHAR_MAX));
            stampChild(attribute::setAddTime, attribute::setUpdateTime, attribute::setDeleted, now);
            aggregate.getAttributes().add(attribute);
        }

        // ---- variants -> SKUs (litemall_goods_product) + spec axes (litemall_goods_specification) ----
        List<CjVariant> variants = parseVariants(row.getVariantsJson());
        buildSpecifications(aggregate, variants, now);
        buildProducts(aggregate, variants, retail, row.getSellPrice(), row.getImageUrl(), now);

        // ---- category + brand descriptors (resolved to ids by the promotion service) ----
        aggregate.setCategory(new NativeGoodsAggregate.CategoryRef(
                firstNonBlank(parseStringList(row.getCategoryIds())),
                parseStringList(row.getCategoryNames())));
        if (StringUtils.hasText(row.getBrand())) {
            aggregate.setBrand(new NativeGoodsAggregate.BrandRef(trim(row.getBrand().trim(), VARCHAR_MAX)));
        }

        return aggregate;
    }

    /**
     * Derives one spec axis per option position. CJ does not name its variant axes, so we
     * use a stable generic name ("Specification", or "Specification N" when multi-axis).
     * The single-element-options case (the common CJ shape) collapses to one clean axis.
     */
    private void buildSpecifications(NativeGoodsAggregate aggregate, List<CjVariant> variants, LocalDateTime now) {
        int arity = variants.stream().mapToInt(v -> v.options().size()).max().orElse(0);
        if (arity == 0) {
            // no usable variant options -> a single placeholder axis so the SPA has something to bind
            aggregate.getSpecifications().add(specification(SPEC_AXIS, DEFAULT_SPEC_VALUE, now));
            return;
        }
        List<LinkedHashSet<String>> axisValues = new ArrayList<>();
        for (int i = 0; i < arity; i++) {
            axisValues.add(new LinkedHashSet<>());
        }
        for (CjVariant v : variants) {
            for (int i = 0; i < v.options().size(); i++) {
                String value = v.options().get(i);
                if (StringUtils.hasText(value)) {
                    axisValues.get(i).add(value);
                }
            }
        }
        for (int i = 0; i < arity; i++) {
            String axisName = arity == 1 ? SPEC_AXIS : SPEC_AXIS + " " + (i + 1);
            for (String value : axisValues.get(i)) {
                aggregate.getSpecifications().add(specification(axisName, value, now));
            }
        }
    }

    private void buildProducts(NativeGoodsAggregate aggregate, List<CjVariant> variants,
                               BigDecimal fallbackPrice, BigDecimal fallbackCost,
                               String imageUrl, LocalDateTime now) {
        for (CjVariant v : variants) {
            LitemallGoodsProduct product = new LitemallGoodsProduct();
            product.setCjVid(v.vid());
            // Charge-integrity guard (Wave 12 repricing): checkout money reads THIS price. When
            // the row is priced on the new cost basis (sell_price captured) but the variant entry
            // predates it (no variant_sell_price → its variant_price is still the old-basis
            // markup), that stale price must NOT survive the repricing — charge the product
            // retail until enrichment recomputes real per-variant prices from per-variant costs.
            boolean staleBasis = fallbackCost != null && v.variantSellPrice() == null;
            product.setPrice(v.variantPrice() != null && !staleBasis ? v.variantPrice() : fallbackPrice);
            product.setCost(v.variantSellPrice() != null ? v.variantSellPrice() : fallbackCost);
            product.setNumber(v.stock() != null ? v.stock() : 0);
            // Wave 25.1: SKU photo = the captured per-variant image when usable, else the main
            // photo. Both fallbacks stay null (never "") when the main photo is absent, so the
            // selective update can never blank a previously landed url.
            String variantImage = usableVariantImage(v.variantImage());
            product.setUrl(variantImage != null ? variantImage : trim(imageUrl, PRODUCT_URL_MAX));
            product.setSpecifications(specTuple(v));
            stampChild(product::setAddTime, product::setUpdateTime, product::setDeleted, now);
            aggregate.getProducts().add(product);
        }
        if (aggregate.getProducts().isEmpty()) {
            // single synthetic SKU so the detail page, cart and order have a product to bind to
            LitemallGoodsProduct product = new LitemallGoodsProduct();
            product.setCjVid(null);
            product.setPrice(fallbackPrice);
            product.setCost(fallbackCost);
            product.setNumber(0);
            product.setUrl(trim(imageUrl, PRODUCT_URL_MAX));
            product.setSpecifications(new String[]{DEFAULT_SPEC_VALUE});
            stampChild(product::setAddTime, product::setUpdateTime, product::setDeleted, now);
            aggregate.getProducts().add(product);
        }
    }

    /** The spec-value tuple for a SKU; falls back to the variant SKU (then a default) to stay distinct. */
    private String[] specTuple(CjVariant v) {
        if (!v.options().isEmpty()) {
            return v.options().toArray(new String[0]);
        }
        if (StringUtils.hasText(v.variantSku())) {
            return new String[]{v.variantSku()};
        }
        return new String[]{DEFAULT_SPEC_VALUE};
    }

    private LitemallGoodsSpecification specification(String name, String value, LocalDateTime now) {
        LitemallGoodsSpecification spec = new LitemallGoodsSpecification();
        spec.setSpecification(trim(name, VARCHAR_MAX));
        spec.setValue(trim(value, VARCHAR_MAX));
        spec.setPicUrl("");
        stampChild(spec::setAddTime, spec::setUpdateTime, spec::setDeleted, now);
        return spec;
    }

    // ---------------------------------------------------------------------------------------------
    // JSON parsing helpers — defensive: malformed/empty input degrades to an empty result, never throws.
    // ---------------------------------------------------------------------------------------------

    private List<CjVariant> parseVariants(String json) {
        List<CjVariant> out = new ArrayList<>();
        JsonNode root = readTree(json);
        if (root == null || !root.isArray()) {
            return out;
        }
        for (JsonNode node : root) {
            String vid = text(node, "vid");
            String sku = text(node, "variant_sku");
            List<String> options = new ArrayList<>();
            JsonNode optionsNode = node.get("options");
            if (optionsNode != null && optionsNode.isArray()) {
                for (JsonNode opt : optionsNode) {
                    if (opt != null && !opt.isNull() && StringUtils.hasText(opt.asText())) {
                        options.add(opt.asText().trim());
                    }
                }
            }
            BigDecimal price = decimal(node, "variant_price");
            BigDecimal sellPrice = decimal(node, "variant_sell_price");
            Integer stock = node.hasNonNull("stock") ? node.get("stock").asInt() : null;
            out.add(new CjVariant(vid, sku, options, price, sellPrice, stock, text(node, "variant_image")));
        }
        return out;
    }

    private Map<String, String> parseFlatMap(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonNode root = readTree(json);
        if (root == null || !root.isObject()) {
            return out;
        }
        root.fields().forEachRemaining(e -> {
            JsonNode value = e.getValue();
            if (value != null && !value.isNull()) {
                out.put(e.getKey(), value.asText());
            }
        });
        return out;
    }

    private List<String> parseStringList(String json) {
        List<String> out = new ArrayList<>();
        JsonNode root = readTree(json);
        if (root == null || !root.isArray()) {
            return out;
        }
        for (JsonNode node : root) {
            if (node != null && !node.isNull() && StringUtils.hasText(node.asText())) {
                out.add(node.asText().trim());
            }
        }
        return out;
    }

    /** Takes images in order until the serialized JSON array would exceed the gallery column budget. */
    private String[] boundedGallery(List<String> images) {
        List<String> kept = new ArrayList<>();
        int jsonLen = 2; // the surrounding [ ]
        for (String url : images) {
            int add = url.length() + 3; // quotes + comma
            if (!kept.isEmpty() && jsonLen + add > GALLERY_JSON_BUDGET) {
                log.debug("gallery truncated at {} images to fit the goods.gallery column", kept.size());
                break;
            }
            kept.add(url);
            jsonLen += add;
        }
        return kept.toArray(new String[0]);
    }

    private JsonNode readTree(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            log.warn("could not parse CJ JSON column ({} chars): {}", json.length(), ex.getMessage());
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() || !StringUtils.hasText(v.asText()) ? null : v.asText().trim();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !StringUtils.hasText(v.asText())) {
            return null;
        }
        try {
            return new BigDecimal(v.asText());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String firstNonBlank(List<String> values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }

    /**
     * Wave 25 hygiene: a usable unit passes through (trimmed to the column width); an absent one —
     * or a CJK glyph unit ({@code 件}/{@code 盒}/…, which must never render beside a € price) —
     * normalizes to blank.
     */
    static String normalizeUnit(String raw) {
        if (!StringUtils.hasText(raw)) {
            return DEFAULT_UNIT;
        }
        String t = raw.trim();
        boolean hasHan = t.codePoints()
                .anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
        return hasHan ? DEFAULT_UNIT : trim(t, UNIT_MAX);
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static void stampChild(java.util.function.Consumer<LocalDateTime> addTime,
                                   java.util.function.Consumer<LocalDateTime> updateTime,
                                   java.util.function.Consumer<Boolean> deleted,
                                   LocalDateTime now) {
        addTime.accept(now);
        updateTime.accept(now);
        deleted.accept(Boolean.FALSE);
    }

    /**
     * Wave 25.1: a per-variant image is usable as the SKU {@code url} only when it is a plain
     * http(s) URL on a {@code /_cdn}-covered CJ host AND fits the varchar(125) column. Anything
     * else (blank, foreign host, oversized) ⇒ null — callers fall back to the main photo; the
     * image is never truncated and an existing url is never blanked.
     */
    public static String usableVariantImage(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim();
        if (t.length() > PRODUCT_URL_MAX) {
            return null;
        }
        for (String prefix : CDN_COVERED_IMAGE_PREFIXES) {
            if (t.startsWith(prefix)) {
                return t;
            }
        }
        return null;
    }

    /** Parsed view of one entry in {@code variants_json}. */
    private record CjVariant(String vid, String variantSku, List<String> options,
                             BigDecimal variantPrice, BigDecimal variantSellPrice, Integer stock,
                             String variantImage) {
    }
}
