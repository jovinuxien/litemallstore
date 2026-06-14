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
 * CJ detail ({@code product/query}) and maps it into the SAME key shape the local detail returns
 * ({@code info / productList / specificationList / attribute / brand / issue / comment / groupon /
 * share / shareImage}) so the customer SPA renders a CJ hit with no branch. Sections CJ has no data
 * for ({@code issue / comment / groupon / userHasCollect}) come back empty/zero.
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

        // info — the LitemallGoods-shaped header the SPA reads (id is the cj_<pid> String, so this
        // is a map, not a LitemallGoods whose id is an int).
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", docId);
        info.put("name", title(d));
        info.put("brief", d.getCategoryName());
        info.put("detail", d.getDescription());
        info.put("picUrl", d.getProductImage());
        info.put("gallery", galleryOf(d, variants));
        info.put("retailPrice", retail);
        info.put("counterPrice", retail);
        info.put("isOnSale", Boolean.TRUE);
        info.put("isHot", Boolean.FALSE);
        info.put("isNew", Boolean.FALSE);
        info.put("categoryId", null);
        info.put("brandId", 0);
        info.put("shareUrl", null);
        info.put("source", CjProductIndexingService.SOURCE_CJ);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("info", info);
        data.put("userHasCollect", 0);
        data.put("issue", List.of());
        data.put("comment", Map.of("count", 0, "data", List.of()));
        data.put("specificationList", specificationList(variants));
        data.put("productList", productList(docId, variants, retail));
        data.put("attribute", attributes(d));
        data.put("brand", brand(d));
        data.put("groupon", List.of());
        data.put("share", Boolean.FALSE);
        data.put("shareImage", null);
        return ResponseUtil.ok(data);
    }

    private static String title(CJProductDetailData d) {
        if (d.getProductNameEn() != null && !d.getProductNameEn().isBlank()) {
            return d.getProductNameEn().trim();
        }
        return d.getProductName();
    }

    private List<String> galleryOf(CJProductDetailData d, List<CJProductVariantData> variants) {
        List<String> gallery = new ArrayList<>();
        if (d.getProductImage() != null) {
            gallery.add(d.getProductImage());
        }
        return gallery;
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
            p.put("id", v.getVid());
            p.put("goodsId", docId);
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

    /**
     * A single specification group ("Specification") whose values are the distinct variant values —
     * enough for the page to render the variant picker. Multi-axis CJ variants (colour + size) are
     * flattened here; full per-axis specs are a follow-up.
     */
    private List<Map<String, Object>> specificationList(List<CJProductVariantData> variants) {
        List<Map<String, Object>> valueList = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (CJProductVariantData v : variants) {
            for (String value : variantValues(v)) {
                if (value != null && !value.isBlank() && !seen.contains(value)) {
                    seen.add(value);
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", seen.size());
                    entry.put("value", value);
                    entry.put("picUrl", null);
                    valueList.add(entry);
                }
            }
        }
        if (valueList.isEmpty()) {
            return List.of();
        }
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("name", "Specification");
        group.put("valueList", valueList);
        return List.of(group);
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
        if (value != null && !value.isBlank()) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("attribute", name);
            a.put("value", value);
            attrs.add(a);
        }
    }

    /** CJ has no brand for most items; expose supplier name as a thin brand stand-in. */
    private Map<String, Object> brand(CJProductDetailData d) {
        Map<String, Object> brand = new LinkedHashMap<>();
        brand.put("id", 0);
        brand.put("name", d.getSupplierName());
        return brand;
    }
}
