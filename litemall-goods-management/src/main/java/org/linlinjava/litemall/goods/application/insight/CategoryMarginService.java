package org.linlinjava.litemall.goods.application.insight;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.dao.LitemallCategoryMarginMapper;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallCategoryMargin;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.goods.application.goods.CatalogGoodsCountService;
import org.linlinjava.litemall.goods.application.pricing.CategoryMarginResolver;
import org.linlinjava.litemall.goods.infrastructure.configuration.InventoryFlowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Wave-14 per-category margin tuning: SIMULATE (pure SQL read over costed on-sale goods, no
 * price mutation) and the override CRUD ({@code litemall_category_margin}, L1 roots only,
 * bounds 1.05–3.0). Overrides take effect at the nightly reprice sites — sync, enrichment and
 * promote all resolve through {@link CategoryMarginResolver}, whose cache is invalidated here
 * so a PUT applies to the very next cycle (and to live CJ-detail fallbacks immediately).
 */
@Service
public class CategoryMarginService {

    private static final Logger log = LoggerFactory.getLogger(CategoryMarginService.class);

    static final BigDecimal MARGIN_MIN = new BigDecimal("1.05");
    static final BigDecimal MARGIN_MAX = new BigDecimal("3.0");

    private final LitemallCategoryMarginMapper marginMapper;
    private final LitemallCategoryService categoryService;
    private final CatalogGoodsCountService countService;
    private final InsightMapper insightMapper;
    private final CategoryMarginResolver resolver;
    private final InventoryFlowProperties flowProperties;

    public CategoryMarginService(LitemallCategoryMarginMapper marginMapper,
                                 LitemallCategoryService categoryService,
                                 CatalogGoodsCountService countService,
                                 InsightMapper insightMapper,
                                 CategoryMarginResolver resolver,
                                 InventoryFlowProperties flowProperties) {
        this.marginMapper = marginMapper;
        this.categoryService = categoryService;
        this.countService = countService;
        this.insightMapper = insightMapper;
        this.resolver = resolver;
        this.flowProperties = flowProperties;
    }

    public GovernanceResult overrides() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LitemallCategoryMargin override : marginMapper.selectAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("categoryId", override.getCategoryId());
            LitemallCategory category = categoryService.findById(override.getCategoryId());
            row.put("name", category != null ? category.getName() : null);
            row.put("margin", override.getMargin());
            row.put("updateTime", override.getUpdateTime() != null
                    ? override.getUpdateTime().toString() : null);
            list.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("globalMargin", resolver.globalMargin());
        return GovernanceResult.ok(data);
    }

    public GovernanceResult put(int categoryId, BigDecimal margin) {
        GovernanceResult invalid = validate(categoryId, margin);
        if (invalid != null) {
            return invalid;
        }
        LitemallCategoryMargin override = new LitemallCategoryMargin();
        override.setCategoryId(categoryId);
        override.setMargin(margin);
        marginMapper.upsert(override);
        resolver.invalidateOverrides();
        log.info("category margin override: root {} → {} (global {})",
                categoryId, margin, resolver.globalMargin());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("categoryId", categoryId);
        data.put("margin", margin);
        return GovernanceResult.ok(data);
    }

    public GovernanceResult delete(int categoryId) {
        int removed = marginMapper.deleteById(categoryId);
        resolver.invalidateOverrides();
        if (removed > 0) {
            log.info("category margin override removed: root {} — global {} applies again",
                    categoryId, resolver.globalMargin());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("categoryId", categoryId);
        data.put("removed", removed > 0);
        return GovernanceResult.ok(data);
    }

    /** Pure read: what the subtree's costed on-sale goods would price at under {@code margin}. */
    public GovernanceResult simulate(int categoryId, BigDecimal margin) {
        GovernanceResult invalid = validate(categoryId, margin);
        if (invalid != null) {
            return invalid;
        }
        Map<String, Object> agg = insightMapper.selectMarginSimulate(
                countService.subtreeIds(categoryId), margin, flowProperties.getProfitStockCap());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("currentMargin", resolver.effectiveForRoot(categoryId));
        data.put("simulatedMargin", margin);
        data.put("goodsCount", agg.get("goodsCount"));
        data.put("avgPriceNow", agg.get("avgPriceNow"));
        data.put("avgPriceAt", agg.get("avgPriceAt"));
        data.put("potentialProfitNow", agg.get("potentialProfitNow"));
        data.put("potentialProfitAt", agg.get("potentialProfitAt"));
        return GovernanceResult.ok(data);
    }

    private GovernanceResult validate(int categoryId, BigDecimal margin) {
        if (margin == null || margin.compareTo(MARGIN_MIN) < 0 || margin.compareTo(MARGIN_MAX) > 0) {
            return GovernanceResult.fail(402, "margin must be between " + MARGIN_MIN
                    + " and " + MARGIN_MAX);
        }
        LitemallCategory category = categoryService.findById(categoryId);
        if (category == null || category.getPid() == null || category.getPid() != 0) {
            return GovernanceResult.fail(402, "category " + categoryId + " is not an L1 root");
        }
        return null;
    }
}
