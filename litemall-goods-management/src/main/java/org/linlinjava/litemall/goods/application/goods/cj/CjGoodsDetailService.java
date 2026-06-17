package org.linlinjava.litemall.goods.application.goods.cj;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productdetail.CJProductDetailData;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productvariant.CJProductVariantData;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.linlinjava.litemall.goods.domain.service.elastic.CjProductIndexingService;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the product-detail payload for a CJ Dropshipping product, on demand.
 *
 * <p>CJ products live in OCS only (index-only ADR — no {@code litemall_goods} row), so the local
 * DB-backed {@code /srv/goods/detail} aggregation can't serve them. This service fetches the live
 * CJ detail ({@code product/query}) and maps it into the SAME key shape the local detail returns —
 * {@code goods / products / specifications / attributes / categoryIds} — so the customer SPA's
 * Detail page renders a CJ hit with no branch. (An earlier version emitted the legacy wx keys
 * {@code info / productList / specificationList / attribute}; the rebuilt SPA reads
 * {@code goods / products / specifications / attributes}, so CJ pages rendered blank.) Field names
 * mirror the local aggregates: {@code goods.goodsId:{id} / goodsName}, flat
 * {@code specifications[{specifications,value,picUrl}]}, {@code attributes[{attributeName,
 * attributeValue}]}, {@code products[{goodsProductId:{id},specifications[]}]}.
 *
 * <p>Pricing mirrors {@code CjProductIndexingService}: retail = CJ wholesale (USD) × usdToCny ×
 * margin in the local CNY basis — never raw wholesale cost.
 */
@Service
public class CjGoodsDetailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CjGoodsDetailService.class);

    /** Same prefix the index/search path uses ({@code CjProductIndexingService.CJ_ID_PREFIX}). */
    public static final String CJ_ID_PREFIX = "cj_";

    private final CJProductService cjProductService;
    private final CJDropshippingConfig config;
    private final ObjectMapper objectMapper;

    public CjGoodsDetailService(CJProductService cjProductService,
                                CJDropshippingConfig config,
                                ObjectMapper objectMapper) {
        this.cjProductService = cjProductService;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /** {@code true} when the id is a CJ document id (and {@link #pidOf} can extract its pid). */
    public static boolean isCjId(String id) {
        return id != null && id.startsWith(CJ_ID_PREFIX);
    }

    /** Strip the {@code cj_} namespace prefix to recover the raw CJ UUID pid. */
    public static String pidOf(String cjId) {
        return cjId.substring(CJ_ID_PREFIX.length());
    }

    /**
     * Fetch + map a CJ product detail by its {@code cj_<pid>} id. Returns a {@code ResponseUtil}
     * envelope — {@code ok(data)} on success, {@code badArgumentValue()} when CJ has no such product.
     */
    public Object detail(String cjId) {
        String pid = pidOf(cjId);
        CJProductDetailData d = cjProductService.getProductDetail(pid);
        if (d == null) {
            LOGGER.info("CJ detail miss for pid {}", pid);
            return ResponseUtil.badArgumentValue();
        }

        String docId = CJ_ID_PREFIX + pid;
        BigDecimal retail = retailPrice(d.getSellPrice());
        List<CJProductVariantData> variants = d.getVariants() != null ? d.getVariants() : List.of();
        List<String> images = imagesOf(d.getProductImage());
        String picUrl = images.isEmpty() ? null : images.get(0);

        // goods — the LitemallGoods-shaped header the SPA reads. goodsId mirrors the local value
        // object ({id}); for CJ the id is the cj_<pid> String (goodId() reads it through verbatim).
        Map<String, Object> goods = new LinkedHashMap<>();
        goods.put("goodsId", Map.of("id", docId));
        goods.put("goodsSn", docId);
        goods.put("goodsName", title(d));
        goods.put("brief", d.getCategoryName());
        goods.put("detail", d.getDescription());
        goods.put("picUrl", picUrl);
        goods.put("gallery", images);
        goods.put("retailPrice", retail);
        goods.put("counterPrice", retail);
        goods.put("categoryId", null);
        goods.put("manufacturerId", null);
        goods.put("keyword", null);
        goods.put("unit", d.getProductUnit());
        goods.put("onSale", Boolean.TRUE);
        goods.put("hot", Boolean.FALSE);
        goods.put("new", Boolean.FALSE);
        goods.put("sortOrder", 0);
        goods.put("shareUrl", null);
        goods.put("source", CjProductIndexingService.SOURCE_CJ);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("goods", goods);
        data.put("products", productList(docId, variants, retail));
        data.put("specifications", specificationList(variants));
        data.put("attributes", attributes(d));
        data.put("categoryIds", List.of());
        return ResponseUtil.ok(data);
    }

    /**
     * Normalize the CJ {@code productImage} field, which can be a plain URL or a JSON-stringified
     * array ({@code "[\"https://…\"]"}). Returns a clean list of URLs so the SPA's
     * {@code <img src>} and gallery get real strings, not a bracketed blob.
     */
    private List<String> imagesOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String t = raw.trim();
        if (t.startsWith("[")) {
            try {
                String[] arr = objectMapper.readValue(t, String[].class);
                List<String> out = new ArrayList<>();
                for (String u : arr) {
                    if (u != null && !u.isBlank()) {
                        out.add(u.trim());
                    }
                }
                return out;
            } catch (Exception e) {
                // fall through to the raw value
            }
        }
        return List.of(t);
    }

    private static String title(CJProductDetailData d) {
        if (d.getProductNameEn() != null && !d.getProductNameEn().isBlank()) {
            return d.getProductNameEn().trim();
        }
        return d.getProductName();
    }

    /** Retail = wholesale USD × usdToCny × margin, in the local CNY basis; null on no cost. */
    private BigDecimal retailPrice(Double sellPrice) {
        if (sellPrice == null) {
            return null;
        }
        CJDropshippingConfig.Pricing pricing = config.getPricing();
        return BigDecimal.valueOf(sellPrice)
                .multiply(pricing.getUsdToCny())
                .multiply(pricing.getMargin())
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * One LitemallGoodsProduct-shaped row per CJ variant: {@code specifications} = the parsed
     * variantKey values, {@code price} = the variant's retail price (falls back to the product
     * retail), {@code number} = the configured default stock.
     */
    private List<Map<String, Object>> productList(String docId, List<CJProductVariantData> variants, BigDecimal productRetail) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CJProductVariantData v : variants) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("goodsProductId", idMap(v.getVid()));
            p.put("goodsId", idMap(docId));
            p.put("specifications", variantValues(v));
            BigDecimal price = retailPrice(v.getVariantSellPrice());
            p.put("price", price != null ? price : productRetail);
            p.put("number", config.getDefaultStock());
            p.put("url", null);
            p.put("variantSku", v.getVariantSku());
            out.add(p);
        }
        return out;
    }

    /** A {@code {id}} value-object wrapper that tolerates a null id (Map.of does not). */
    private static Map<String, Object> idMap(Object id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        return m;
    }

    /**
     * Flat specification rows in the LOCAL shape ({@code {specifications, value, picUrl}}) — the SPA
     * groups them by the {@code specifications} group-name. A single "Specification" group whose
     * values are the distinct variant values is enough for the page to render the variant picker.
     * Multi-axis CJ variants (colour + size) are flattened here; full per-axis specs are a follow-up.
     */
    private List<Map<String, Object>> specificationList(List<CJProductVariantData> variants) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (CJProductVariantData v : variants) {
            for (String value : variantValues(v)) {
                if (value != null && !value.isBlank() && !seen.contains(value)) {
                    seen.add(value);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("specifications", "Specification");
                    row.put("value", value);
                    row.put("picUrl", null);
                    out.add(row);
                }
            }
        }
        return out;
    }

    /** Parse a CJ {@code variantKey} (e.g. {@code ["Black","XL"]}) into its value strings. */
    private List<String> variantValues(CJProductVariantData v) {
        String key = v.getVariantKey();
        if (key == null || key.isBlank()) {
            String name = v.getVariantNameEn() != null ? v.getVariantNameEn() : v.getVariantName();
            return name != null ? List.of(name) : List.of();
        }
        try {
            String[] parsed = objectMapper.readValue(key, String[].class);
            return List.of(parsed);
        } catch (Exception e) {
            return List.of(key.trim());
        }
    }

    /** Surface CJ's physical fields as goods attributes (CJ has no merchandised attribute set). */
    private List<Map<String, Object>> attributes(CJProductDetailData d) {
        List<Map<String, Object>> attrs = new ArrayList<>();
        addAttr(attrs, "Material", d.getMaterialNameEn() != null ? d.getMaterialNameEn() : d.getMaterialName());
        addAttr(attrs, "Weight", d.getProductWeight());
        addAttr(attrs, "Unit", d.getProductUnit());
        addAttr(attrs, "Supplier", d.getSupplierName());
        return attrs;
    }

    private void addAttr(List<Map<String, Object>> attrs, String name, String value) {
        String readable = readable(value);
        if (readable != null && !readable.isBlank()) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("attributeName", name);
            a.put("attributeValue", readable);
            attrs.add(a);
        }
    }

    /**
     * Some CJ fields (e.g. materialNameEn) come back JSON-stringified ({@code "[\"Cloth\"]"}).
     * Flatten those to a readable comma-joined string; pass plain values through untouched.
     */
    private String readable(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.startsWith("[")) {
            try {
                String[] arr = objectMapper.readValue(t, String[].class);
                return String.join(", ", arr);
            } catch (Exception e) {
                // fall through to the raw value
            }
        }
        return t;
    }
}
