package org.linlinjava.litemall.goods.application.engagement;

import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.springframework.stereotype.Component;

/**
 * Resolves the goods reference the customer SPA sends on the engagement write
 * paths (collect / footprint / comment-post) to a live {@code litemall_goods}
 * row. The reference is either a numeric goods id or the {@code cj_&lt;pid&gt;}
 * doc id the index-only CJ detail page navigates with; since the CJ catalog
 * merge every indexed CJ product has a promoted native row, so a pid resolves
 * through {@code uk_goods_source_cjpid} and the engagement tables only ever
 * store numeric goods ids (their {@code value_id}/{@code goods_id} columns are
 * INT).
 */
@Component
public class EngagementGoodsResolver {

    private final LitemallGoodsService goodsService;
    private final LitemallCjLinkageMapper linkageMapper;

    public EngagementGoodsResolver(LitemallGoodsService goodsService,
                                   LitemallCjLinkageMapper linkageMapper) {
        this.goodsService = goodsService;
        this.linkageMapper = linkageMapper;
    }

    /** The live goods row for a numeric id or {@code cj_<pid>} reference, or null. */
    public LitemallGoods resolve(String goodsRef) {
        Integer id = resolveId(goodsRef);
        return id == null ? null : goodsService.findById(id);
    }

    /** The live goods id for a numeric id or {@code cj_<pid>} reference, or null. */
    public Integer resolveId(String goodsRef) {
        if (goodsRef == null || goodsRef.isBlank()) {
            return null;
        }
        if (goodsRef.startsWith("cj_")) {
            String pid = goodsRef.substring(3);
            return pid.isBlank() ? null : linkageMapper.findGoodsIdByCjPid(pid);
        }
        try {
            return Integer.valueOf(goodsRef.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
