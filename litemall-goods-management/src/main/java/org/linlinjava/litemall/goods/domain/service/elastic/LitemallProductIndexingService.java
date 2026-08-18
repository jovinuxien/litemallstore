package org.linlinjava.litemall.goods.domain.service.elastic;


import org.linlinjava.litemall.db.domain.LitemallBrand;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsAttribute;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.db.domain.LitemallSeckill;
import org.linlinjava.litemall.db.service.LitemallBrandService;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallGoodsAttributeService;
import org.linlinjava.litemall.db.service.LitemallGoodsProductService;
import org.linlinjava.litemall.db.service.LitemallSeckillService;
import org.linlinjava.litemall.goods.domain.deals.DealMath;
import org.linlinjava.litemall.goods.domain.model.valueobjects.elastic.ProductDocument;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSearchProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps a {@link LitemallGoods} to the flat OCS {@link ProductDocument} shape.
 * The OCS indexer service owns the Elasticsearch mapping; this service only
 * builds the JSON-serializable document — pushing it to OCS is the
 * {@link ProductIndexer} port's job (implemented by the
 * {@code infrastructure/acl/ocs} adapter).
 *
 * <p>Beyond the nine fixed fields it enriches each document with curated facetable
 * {@code litemall_goods_attribute} values (→ dynamic-field facets) and per-SKU
 * {@code litemall_goods_product} variant data ({@code variant_price}/{@code stock}).
 */
@Service
public class LitemallProductIndexingService {

    private final LitemallBrandService brandService;
    private final LitemallCategoryService categoryService;
    private final LitemallGoodsAttributeService attributeService;
    private final LitemallGoodsProductService productService;
    private final LitemallSeckillService seckillService;
    private final CouponSignalResolver couponSignalResolver;
    private final GrouponSignalResolver grouponSignalResolver;
    private final EuStockSignalResolver euStockSignalResolver;
    private final LitemallSearchProperties properties;

    public LitemallProductIndexingService(LitemallBrandService brandService,
                                          LitemallCategoryService categoryService,
                                          LitemallGoodsAttributeService attributeService,
                                          LitemallGoodsProductService productService,
                                          LitemallSeckillService seckillService,
                                          CouponSignalResolver couponSignalResolver,
                                          GrouponSignalResolver grouponSignalResolver,
                                          EuStockSignalResolver euStockSignalResolver,
                                          LitemallSearchProperties properties) {
        this.brandService = brandService;
        this.categoryService = categoryService;
        this.attributeService = attributeService;
        this.productService = productService;
        this.seckillService = seckillService;
        this.couponSignalResolver = couponSignalResolver;
        this.grouponSignalResolver = grouponSignalResolver;
        this.euStockSignalResolver = euStockSignalResolver;
        this.properties = properties;
    }

    public ProductDocument createProductDocument(LitemallGoods goods) {
        ProductDocument doc = new ProductDocument();
        doc.setProductId(String.valueOf(goods.getId()));
        // Carry the row's origin so OCS can still filter/badge by source now that CJ products live in
        // litemall_goods (source='cj') and flow through this same native path; default to 'local'.
        doc.setSource(goods.getSource() != null ? goods.getSource() : "local");
        doc.setTitle(goods.getName());
        doc.setDescription(goods.getBrief());
        doc.setImageUrl(goods.getPicUrl());

        BigDecimal counter = goods.getCounterPrice();
        BigDecimal retail = goods.getRetailPrice();
        int discountPct = 0;
        if (counter != null && retail != null && counter.compareTo(retail) > 0) {
            doc.setPrice(counter);
            doc.setDiscountPrice(retail);
            if (counter.signum() > 0) {
                discountPct = BigDecimal.ONE
                        .subtract(retail.divide(counter, 4, RoundingMode.HALF_UP))
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(0, RoundingMode.HALF_UP)
                        .intValue();
            }
        } else {
            doc.setPrice(retail);
            doc.setDiscountPrice(null);
        }
        // Deal signals: whole-percent markdown + threshold flag. CJ goods carry counter==retail
        // (suggestSellPrice is a recommendation, not an MSRP) so they naturally index 0/0 —
        // enrichment-on-view keeps flowing through this same method unchanged.
        doc.setDiscountPct(discountPct);
        doc.setDealFlag(discountPct >= properties.getDealMinPct() ? 1 : 0);

        // Live flash deal (price_swapped=1): the swap already put the deal price on the goods
        // row, so price/discount_pct above reflect it — here we add the time-boxed extras the
        // deals surfaces need. The lifecycle scheduler reindexes on every transition and on
        // urgency/claimed drift. The three queryable fields (Filter/Sort/Score) are emitted on
        // EVERY document with no-deal defaults — see DealMath.NO_DEAL_END_EPOCH for why a
        // conditional field silently breaks OCS field resolution after a deal-less full reindex.
        LitemallSeckill liveDeal = seckillService.findLiveByGoodsId(goods.getId());
        if (liveDeal != null) {
            doc.setDealActive(1);
            doc.setDealEndEpoch(DealMath.toEpochMilli(liveDeal.getStopTime()));
            doc.setDealClaimedPct(DealMath.claimedPct(liveDeal));
            doc.setDealUrgency(DealMath.urgencyOf(liveDeal.getStopTime()));
        } else {
            doc.setDealActive(0);
            doc.setDealEndEpoch(DealMath.NO_DEAL_END_EPOCH);
            doc.setDealUrgency(0); // ln2p(0) = the uniform ~0.69 baseline — scoring unchanged
        }

        if (goods.getBrandId() != null) {
            LitemallBrand brand = brandService.findById(goods.getBrandId());
            if (brand != null) {
                doc.setBrand(brand.getName());
            }
        }

        List<String> categoryNames = new ArrayList<>();
        List<String> categoryIds = new ArrayList<>();
        if (goods.getCategoryId() != null) {
            LitemallCategory category = categoryService.findById(goods.getCategoryId());
            while (category != null) {
                categoryNames.add(category.getName());
                categoryIds.add(String.valueOf(category.getId()));
                Integer parentId = category.getPid();
                if (parentId == null || parentId.equals(0)) {
                    break;
                }
                category = categoryService.findById(parentId);
            }
            Collections.reverse(categoryNames);
            Collections.reverse(categoryIds);
        }
        doc.setCategoryNames(categoryNames);
        doc.setCategoryIds(categoryIds);

        // Wave-19 coupon visibility: matched against the FULL ancestor chain built above, so a
        // coupon scoped at any category level flags every product in its subtree. Always 1/0
        // (never absent) — the deal-fields always-emit rule.
        doc.setCouponFlag(couponSignalResolver.couponFlag(goods.getId(), categoryIds));

        // Wave-21 group-buy visibility: 1 when an ACTIVE in-window litemall_combination
        // campaign targets this goods. Always 1/0 (never absent) — the deal-fields
        // always-emit rule.
        doc.setGrouponFlag(grouponSignalResolver.grouponFlag(goods.getId()));

        // Wave-27 EU-warehouse visibility: 1 when this product's last inventory probe measured EU
        // stock. Keyed on cj_pid, not goods id — the reading lives on the CJ snapshot row. Always
        // 1/0 (never absent) — the deal-fields always-emit rule.
        doc.setEuFlag(euStockSignalResolver.euFlag(goods.getCjPid()));

        // V31 ranking signals, read straight off the goods row (populated for CJ at promote, for
        // local by the review aggregator). Emitted as master-level numeric fields the searcher's
        // scoring-configuration multiplies into relevance and the discovery rails sort on. A null
        // becomes absent in the document → OCS treats it as neutral (0/missing).
        // Emit 0 (not absent) for a missing signal so the searcher's ln2p(2+x) factor is a uniform
        // ~0.69 for every no-signal product (local goods legitimately carry listed_num 0) — no
        // product is ever zeroed, and no source is structurally buried; products rise only on the
        // signals they actually have.
        doc.setListedNum(goods.getListedNum() != null ? goods.getListedNum() : 0);
        doc.setReviewCount(goods.getReviewCount() != null ? goods.getReviewCount() : 0);
        doc.setRating(goods.getRating() != null ? goods.getRating() : BigDecimal.ZERO);
        if (goods.getAddTime() != null) {
            doc.setCreatedEpoch(goods.getAddTime().toInstant(ZoneOffset.UTC).toEpochMilli());
        }

        addCuratedAttributes(doc, goods.getId());
        addVariants(doc, goods.getId());

        return doc;
    }

    /** Index only the configured facetable attributes (case-insensitive) as flat data keys. */
    private void addCuratedAttributes(ProductDocument doc, Integer goodsId) {
        List<String> allow = properties.getFacetAttributes();
        if (allow == null || allow.isEmpty()) {
            return;
        }
        Set<String> allowed = allow.stream()
                .map(a -> a.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        for (LitemallGoodsAttribute attribute : attributeService.queryByGid(goodsId)) {
            String name = attribute.getAttribute();
            if (name != null && allowed.contains(name.toLowerCase(Locale.ROOT))) {
                doc.addAttribute(name, attribute.getValue());
            }
        }
    }

    /** Build one variant per SKU carrying its own price + stock for variant-level facet/filter. */
    private void addVariants(ProductDocument doc, Integer goodsId) {
        for (LitemallGoodsProduct product : productService.queryByGid(goodsId)) {
            Map<String, Object> variant = new LinkedHashMap<>();
            if (product.getPrice() != null) {
                variant.put("variant_price", product.getPrice());
            }
            if (product.getNumber() != null) {
                variant.put("stock", product.getNumber());
            }
            doc.addVariant(variant);
        }
    }
}
