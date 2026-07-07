package org.linlinjava.litemall.goods.application.engagement;

import com.github.pagehelper.Page;
import org.linlinjava.litemall.db.domain.LitemallFootprint;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallFootprintService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Customer browsing history over the legacy {@code litemall_footprint} table
 * (litemall-wx-api {@code /wx/footprint} parity). Recording is fire-and-forget
 * from the product page and deduped per user+goods+day (upstream behavior).
 */
@Service
public class FootprintService {

    private final LitemallFootprintService footprintService;
    private final LitemallGoodsService goodsService;
    private final EngagementGoodsResolver goodsResolver;

    public FootprintService(LitemallFootprintService footprintService,
                            LitemallGoodsService goodsService,
                            EngagementGoodsResolver goodsResolver) {
        this.footprintService = footprintService;
        this.goodsService = goodsService;
        this.goodsResolver = goodsResolver;
    }

    /** One page of the user's footprints, newest first, goods-enriched. */
    public Map<String, Object> list(Integer userId, Integer page, Integer limit) {
        List<LitemallFootprint> rows = footprintService.queryByAddTime(userId, page, limit);
        long total = rows instanceof Page ? ((Page<?>) rows).getTotal() : rows.size();

        List<Map<String, Object>> list = new ArrayList<>(rows.size());
        for (LitemallFootprint row : rows) {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", row.getId());
            vo.put("goodsId", row.getGoodsId());
            vo.put("addTime", row.getAddTime());
            LitemallGoods goods = goodsService.findById(row.getGoodsId());
            if (goods != null) {
                vo.put("name", goods.getName());
                vo.put("brief", goods.getBrief());
                vo.put("picUrl", goods.getPicUrl());
                vo.put("retailPrice", goods.getRetailPrice());
            }
            list.add(vo);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return data;
    }

    /**
     * Record a product-page view. Unresolvable/dead goods references are
     * ignored (the SPA fires this on every detail view and never reads the
     * result); a same-day visit to the same goods leaves the existing row.
     */
    public void record(Integer userId, String goodsRef) {
        Integer goodsId = goodsResolver.resolveId(goodsRef);
        if (goodsId == null || goodsService.findById(goodsId) == null) {
            return;
        }
        List<LitemallFootprint> latest = footprintService.querySelective(
                String.valueOf(userId), String.valueOf(goodsId), 1, 1, "add_time", "desc");
        if (!latest.isEmpty()) {
            LitemallFootprint newest = latest.get(0);
            if (newest.getAddTime() != null
                    && newest.getAddTime().toLocalDate().equals(LocalDate.now())) {
                return;
            }
        }
        LitemallFootprint footprint = new LitemallFootprint();
        footprint.setUserId(userId);
        footprint.setGoodsId(goodsId);
        footprintService.add(footprint);
    }

    /** Delete one of the caller's own rows; false when the row isn't theirs. */
    public boolean delete(Integer userId, Integer id) {
        LitemallFootprint footprint = footprintService.findById(userId, id);
        if (footprint == null) {
            return false;
        }
        footprintService.deleteById(id);
        return true;
    }
}
