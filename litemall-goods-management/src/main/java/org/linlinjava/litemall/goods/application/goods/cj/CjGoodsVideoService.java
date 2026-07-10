package org.linlinjava.litemall.goods.application.goods.cj;

import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productvideo.CJProductVideo;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anonymous customer read path for CJ product videos ({@code GET /srv/goods/videos?id=}).
 *
 * <p>Accepts BOTH goods-id forms, same resolution as {@code CommentQueryService}: a raw
 * {@code cj_&lt;pid&gt;} doc id from the index-only CJ detail page, or a numeric native goods id
 * whose row is {@code source='cj'} with a {@code cjPid}. Everything else (local goods, unknown
 * ids) — and any CJ failure — degrades to an empty list: videos are a bonus surface that must
 * never break the product page. Live fetches go through the cached/paced {@link CJProductService}.
 */
@Service
public class CjGoodsVideoService {

    private final LitemallGoodsService goodsService;
    private final CJProductService cjProductService;

    public CjGoodsVideoService(LitemallGoodsService goodsService, CJProductService cjProductService) {
        this.goodsService = goodsService;
        this.cjProductService = cjProductService;
    }

    /** Video vos for a goods id (either form); empty list for non-CJ goods, none, or CJ down. */
    public List<Map<String, Object>> videosFor(String id) {
        String pid = resolvePid(id);
        if (pid == null) {
            return List.of();
        }
        List<CJProductVideo> videos = cjProductService.getProductVideos(pid);
        List<Map<String, Object>> voList = new ArrayList<>(videos.size());
        for (CJProductVideo v : videos) {
            // Only relay live videos; CJ marks pulled ones with a non-ON state.
            if (v.getVideoState() != null && !"ON_STATE".equals(v.getVideoState())) {
                continue;
            }
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", v.getId());
            vo.put("name", v.getVideoName());
            vo.put("url", v.getVideoUrl());
            vo.put("duration", v.getDuration());
            vo.put("width", v.getWidth());
            vo.put("height", v.getHeight());
            voList.add(vo);
        }
        return voList;
    }

    private String resolvePid(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        if (CjGoodsDetailService.isCjId(id)) {
            return CjGoodsDetailService.pidOf(id);
        }
        Integer goodsId;
        try {
            goodsId = Integer.valueOf(id.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        LitemallGoods goods = goodsService.findById(goodsId);
        if (goods != null && "cj".equals(goods.getSource()) && StringUtils.hasText(goods.getCjPid())) {
            return goods.getCjPid();
        }
        return null;
    }
}
