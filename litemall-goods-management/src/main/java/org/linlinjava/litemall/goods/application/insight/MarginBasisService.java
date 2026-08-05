package org.linlinjava.litemall.goods.application.insight;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.goods.application.goods.CatalogGoodsCountService;
import org.springframework.stereotype.Service;

/**
 * Wave-18 coupon margin-guard basis (contract:
 * {@code docs/handoff-coupon-margin-basis.md}). One read-only aggregate describing
 * the worst profitability profile of a coupon's goods scope, consumed by
 * promotion-service so it can hard-reject coupons whose worst-case basket would
 * sell below cost x floor. Category ids are subtree-expanded HERE at query time —
 * an L1 pick automatically covers leaves added by later CJ syncs, so the guard
 * never goes stale against a saved leaf list. Both lists empty = the whole
 * on-sale catalog (ALL-scope coupons).
 */
@Service
public class MarginBasisService {

    /** Bounds match the coupon scope caps promotion enforces; requests beyond them are 402s. */
    public static final int MAX_GOODS_IDS = 500;
    public static final int MAX_CATEGORY_IDS = 50;

    private final InsightMapper insightMapper;
    private final CatalogGoodsCountService countService;

    public MarginBasisService(InsightMapper insightMapper, CatalogGoodsCountService countService) {
        this.insightMapper = insightMapper;
        this.countService = countService;
    }

    public Map<String, Object> basis(List<Integer> goodsIds, List<Integer> categoryIds) {
        List<Integer> goods = compact(goodsIds);
        Set<Integer> leafIds = new LinkedHashSet<>();
        for (Integer categoryId : compact(categoryIds)) {
            leafIds.addAll(countService.subtreeIds(categoryId));
        }

        Map<String, Object> row = insightMapper.selectMarginBasis(goods, new ArrayList<>(leafIds));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("onSaleCount", row.get("onSaleCount"));
        data.put("costedCount", row.get("costedCount"));
        data.put("uncostedCount", row.get("uncostedCount"));
        data.put("maxCostRatio", row.get("maxCostRatio"));
        data.put("minRetailPrice", row.get("minRetailPrice"));
        return data;
    }

    private static List<Integer> compact(List<Integer> ids) {
        List<Integer> out = new ArrayList<>();
        if (ids != null) {
            for (Integer id : ids) {
                if (id != null && id > 0 && !out.contains(id)) {
                    out.add(id);
                }
            }
        }
        return out;
    }
}
