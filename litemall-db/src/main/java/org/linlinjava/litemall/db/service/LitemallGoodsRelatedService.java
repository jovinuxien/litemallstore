package org.linlinjava.litemall.db.service;

import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallGoodsRelatedMapper;
import org.linlinjava.litemall.db.domain.LitemallGoodsRelated;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class LitemallGoodsRelatedService {

    @Resource
    private LitemallGoodsRelatedMapper relatedMapper;

    public void upsert(Integer goodsId, String relatedIds) {
        LitemallGoodsRelated row = new LitemallGoodsRelated();
        row.setGoodsId(goodsId);
        row.setRelatedIds(relatedIds == null ? "" : relatedIds);
        relatedMapper.upsert(row);
    }

    public LitemallGoodsRelated findByGoodsId(Integer goodsId) {
        return relatedMapper.selectByGoodsId(goodsId);
    }

    public List<Integer> queryAllGoodsIds() {
        return relatedMapper.selectAllGoodsIds();
    }

    public void deleteByGoodsId(Integer goodsId) {
        relatedMapper.deleteByGoodsId(goodsId);
    }

    /** Co-purchase pair rows {goodsId, relatedId, weight} for the nightly batch. */
    public List<Map<String, Object>> queryCoPurchasePairs() {
        return relatedMapper.selectCoPurchasePairs();
    }

    /** Co-view (footprint, 90-day) pair rows {goodsId, relatedId, weight} for the nightly batch. */
    public List<Map<String, Object>> queryCoViewPairs() {
        return relatedMapper.selectCoViewPairs();
    }
}
