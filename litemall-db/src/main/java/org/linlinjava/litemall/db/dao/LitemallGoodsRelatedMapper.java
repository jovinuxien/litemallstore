package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.linlinjava.litemall.db.domain.LitemallGoodsRelated;

import java.util.List;
import java.util.Map;

@Mapper
public interface LitemallGoodsRelatedMapper {

    @Insert("INSERT INTO litemall_goods_related(goods_id, related_ids, update_time) " +
            "VALUES(#{goodsId}, #{relatedIds}, NOW()) " +
            "ON DUPLICATE KEY UPDATE related_ids = #{relatedIds}, update_time = NOW()")
    int upsert(LitemallGoodsRelated related);

    @Select("SELECT * FROM litemall_goods_related WHERE goods_id = #{goodsId}")
    LitemallGoodsRelated selectByGoodsId(Integer goodsId);

    @Select("SELECT goods_id FROM litemall_goods_related")
    List<Integer> selectAllGoodsIds();

    @Delete("DELETE FROM litemall_goods_related WHERE goods_id = #{goodsId}")
    int deleteByGoodsId(Integer goodsId);

    // --- co-occurrence sources (goods-management nightly batch) ---
    // Rows are {goodsId, relatedId, weight}; both directions of a pair are emitted
    // (the self-join is symmetric), so each goods accumulates its own neighbor list.

    /** Co-purchase pairs: distinct paid-or-later orders containing both goods. */
    @Select("SELECT og1.goods_id AS goodsId, og2.goods_id AS relatedId, " +
            "COUNT(DISTINCT og1.order_id) AS weight " +
            "FROM litemall_order_goods og1 " +
            "JOIN litemall_order_goods og2 ON og1.order_id = og2.order_id AND og1.goods_id != og2.goods_id " +
            "JOIN litemall_order o ON o.id = og1.order_id " +
            "WHERE og1.deleted = 0 AND og2.deleted = 0 AND o.deleted = 0 AND o.order_status >= 201 " +
            "GROUP BY og1.goods_id, og2.goods_id")
    List<Map<String, Object>> selectCoPurchasePairs();

    /**
     * Co-view pairs: distinct users whose recent (90-day) footprints contain both goods.
     * The recency window bounds the per-user self-join — a long-lived heavy browser would
     * otherwise contribute O(n²) pairs forever.
     */
    @Select("SELECT f1.goods_id AS goodsId, f2.goods_id AS relatedId, " +
            "COUNT(DISTINCT f1.user_id) AS weight " +
            "FROM litemall_footprint f1 " +
            "JOIN litemall_footprint f2 ON f1.user_id = f2.user_id AND f1.goods_id != f2.goods_id " +
            "WHERE f1.deleted = 0 AND f2.deleted = 0 " +
            "AND f1.add_time > DATE_SUB(NOW(), INTERVAL 90 DAY) " +
            "AND f2.add_time > DATE_SUB(NOW(), INTERVAL 90 DAY) " +
            "GROUP BY f1.goods_id, f2.goods_id")
    List<Map<String, Object>> selectCoViewPairs();
}
