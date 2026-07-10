package org.linlinjava.litemall.goods.application.engagement;

import com.github.pagehelper.Page;
import org.linlinjava.litemall.db.domain.LitemallCollect;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallCollectService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Customer favorites over the legacy {@code litemall_collect} table
 * (litemall-wx-api {@code /wx/collect} parity). Type 0 = goods; type 1 (topics)
 * is accepted for storage parity but not goods-enriched.
 */
@Service
public class CollectService {

    private final LitemallCollectService collectService;
    private final LitemallGoodsService goodsService;
    private final EngagementGoodsResolver goodsResolver;

    public CollectService(LitemallCollectService collectService,
                          LitemallGoodsService goodsService,
                          EngagementGoodsResolver goodsResolver) {
        this.collectService = collectService;
        this.goodsService = goodsService;
        this.goodsResolver = goodsResolver;
    }

    /** One page of the user's favorites, newest first, goods-enriched. */
    public Map<String, Object> list(Integer userId, Byte type, Integer page, Integer limit) {
        List<LitemallCollect> rows = collectService.queryByType(userId, type, page, limit, "add_time", "desc");
        long total = rows instanceof Page ? ((Page<?>) rows).getTotal() : rows.size();

        List<Map<String, Object>> list = new ArrayList<>(rows.size());
        for (LitemallCollect row : rows) {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", row.getId());
            vo.put("type", row.getType());
            vo.put("valueId", row.getValueId());
            if (row.getType() != null && row.getType() == 0) {
                LitemallGoods goods = goodsService.findById(row.getValueId());
                if (goods != null) {
                    vo.put("name", goods.getName());
                    vo.put("brief", goods.getBrief());
                    vo.put("picUrl", goods.getPicUrl());
                    vo.put("retailPrice", goods.getRetailPrice());
                }
            }
            list.add(vo);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return data;
    }

    /**
     * Toggle a favorite: collected → removed, not collected → added (the SPA's
     * {@code addordelete} semantics). Returns the resulting state, or null when
     * the goods reference does not resolve to a live row.
     */
    public Map<String, Object> addOrDelete(Integer userId, Byte type, String valueRef) {
        Integer valueId;
        if (type != null && type == 0) {
            valueId = goodsResolver.resolveId(valueRef);
            if (valueId == null || goodsService.findById(valueId) == null) {
                return null;
            }
        } else {
            valueId = goodsResolver.resolveId(valueRef);
            if (valueId == null) {
                return null;
            }
        }

        LitemallCollect existing = collectService.queryByTypeAndValue(userId, type, valueId);
        boolean collected;
        if (existing != null) {
            collectService.deleteById(existing.getId());
            collected = false;
        } else {
            LitemallCollect collect = new LitemallCollect();
            collect.setUserId(userId);
            collect.setType(type);
            collect.setValueId(valueId);
            collectService.add(collect);
            collected = true;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("valueId", valueId);
        data.put("collected", collected);
        return data;
    }
}
