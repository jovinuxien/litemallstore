package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallSeckill;

import java.util.List;

@Mapper
public interface LitemallSeckillMapper {

    @Insert("INSERT INTO litemall_seckill(goods_id, goods_name, pic_url, price, cost, stock, sales, quota, quota_show, time, is_postage, postage, temp_id, weight, volume, sort, status, is_del, start_time, stop_time, add_time, update_time, deleted) " +
            "VALUES(#{goodsId}, #{goodsName}, #{picUrl}, #{price}, #{cost}, #{stock}, #{sales}, #{quota}, #{quotaShow}, #{time}, #{isPostage}, #{postage}, #{tempId}, #{weight}, #{volume}, #{sort}, #{status}, #{isDel}, #{startTime}, #{stopTime}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallSeckill seckill);

    @Insert("INSERT INTO litemall_seckill(goods_id, goods_name, pic_url, price, cost, stock, sales, quota, quota_show, time, is_postage, postage, temp_id, weight, volume, sort, status, is_del, start_time, stop_time, add_time, update_time, deleted) " +
            "VALUES(#{goodsId}, #{goodsName}, #{picUrl}, #{price}, #{cost}, #{stock}, #{sales}, #{quota}, #{quotaShow}, #{time}, #{isPostage}, #{postage}, #{tempId}, #{weight}, #{volume}, #{sort}, #{status}, #{isDel}, #{startTime}, #{stopTime}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LitemallSeckill seckill);

    @Select("SELECT * FROM litemall_seckill WHERE id = #{id} AND deleted = 0")
    LitemallSeckill selectByPrimaryKey(Integer id);

    @Select("SELECT * FROM litemall_seckill WHERE status = 1 AND is_del = 0 AND deleted = 0 AND start_time <= NOW() AND stop_time >= NOW() ORDER BY sort ASC")
    List<LitemallSeckill> selectActive();

    @Update("UPDATE litemall_seckill SET deleted = 1, update_time = NOW() WHERE id = #{id}")
    int logicalDeleteByPrimaryKey(Integer id);

    @Update("UPDATE litemall_seckill SET stock = stock - #{quantity}, sales = sales + #{quantity}, update_time = NOW() WHERE id = #{id} AND stock >= #{quantity}")
    int decreaseStock(@Param("id") Integer id, @Param("quantity") Integer quantity);

    @Update("<script>UPDATE litemall_seckill" +
            "<set>" +
            "<if test='goodsName != null'>goods_name = #{goodsName},</if>" +
            "<if test='picUrl != null'>pic_url = #{picUrl},</if>" +
            "<if test='price != null'>price = #{price},</if>" +
            "<if test='cost != null'>cost = #{cost},</if>" +
            "<if test='stock != null'>stock = #{stock},</if>" +
            "<if test='sales != null'>sales = #{sales},</if>" +
            "<if test='quota != null'>quota = #{quota},</if>" +
            "<if test='quotaShow != null'>quota_show = #{quotaShow},</if>" +
            "<if test='time != null'>time = #{time},</if>" +
            "<if test='isPostage != null'>is_postage = #{isPostage},</if>" +
            "<if test='postage != null'>postage = #{postage},</if>" +
            "<if test='sort != null'>sort = #{sort},</if>" +
            "<if test='status != null'>status = #{status},</if>" +
            "<if test='isDel != null'>is_del = #{isDel},</if>" +
            "<if test='startTime != null'>start_time = #{startTime},</if>" +
            "<if test='stopTime != null'>stop_time = #{stopTime},</if>" +
            "<if test='originalRetailPrice != null'>original_retail_price = #{originalRetailPrice},</if>" +
            "<if test='priceSwapped != null'>price_swapped = #{priceSwapped},</if>" +
            "<if test='originalSkuPrices != null'>original_sku_prices = #{originalSkuPrices},</if>" +
            "update_time = NOW()" +
            "</set>" +
            "WHERE id = #{id}</script>")
    int updateByPrimaryKeySelective(LitemallSeckill seckill);

    // --- V38 flash-deal lifecycle queries (goods-management price-swap scheduler) ---

    /** Enabled deals whose window has opened but whose price swap has not been applied yet. */
    @Select("SELECT * FROM litemall_seckill WHERE status = 1 AND is_del = 0 AND deleted = 0 " +
            "AND price_swapped = 0 AND start_time <= NOW() AND stop_time > NOW()")
    List<LitemallSeckill> selectDueForActivation();

    /** Live-swapped deals that must be unwound: window closed, disabled, or deleted. */
    @Select("SELECT * FROM litemall_seckill WHERE price_swapped = 1 " +
            "AND (stop_time <= NOW() OR status = 0 OR is_del = 1 OR deleted = 1)")
    List<LitemallSeckill> selectDueForExpiry();

    /** All currently swapped deals (claimed refresh + sold-out early expiry). */
    @Select("SELECT * FROM litemall_seckill WHERE price_swapped = 1 AND stop_time > NOW() " +
            "AND status = 1 AND is_del = 0 AND deleted = 0")
    List<LitemallSeckill> selectSwapped();

    /** The live (price-swapped) deal for a goods, if any — what the indexer reads. */
    @Select("SELECT * FROM litemall_seckill WHERE goods_id = #{goodsId} AND price_swapped = 1 LIMIT 1")
    LitemallSeckill selectLiveByGoodsId(Integer goodsId);

    /** Goods ids with a live price swap — the CJ promote path skips price writes for these (V40). */
    @Select("SELECT goods_id FROM litemall_seckill WHERE price_swapped = 1")
    List<Integer> selectLiveSwappedGoodsIds();

    /** Enabled deals on the goods whose window overlaps [start, stop), excluding one id (0 = none). */
    @Select("SELECT COUNT(*) FROM litemall_seckill WHERE goods_id = #{goodsId} AND status = 1 " +
            "AND is_del = 0 AND deleted = 0 AND id != #{excludeId} " +
            "AND start_time < #{stop} AND stop_time > #{start}")
    int countOverlapping(@Param("goodsId") Integer goodsId,
                         @Param("start") java.time.LocalDateTime start,
                         @Param("stop") java.time.LocalDateTime stop,
                         @Param("excludeId") Integer excludeId);

    @Select("SELECT * FROM litemall_seckill WHERE deleted = 0 ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<LitemallSeckill> selectAdminPage(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM litemall_seckill WHERE deleted = 0")
    int countAdmin();

    /** Orders-based claimed count: paid-or-later order lines for the goods inside the deal window. */
    @Select("SELECT COALESCE(SUM(og.number), 0) FROM litemall_order_goods og " +
            "JOIN litemall_order o ON o.id = og.order_id " +
            "WHERE og.goods_id = #{goodsId} AND og.deleted = 0 AND o.deleted = 0 " +
            "AND o.order_status >= 201 AND o.pay_time >= #{start} AND o.pay_time < #{stop}")
    int sumPaidQuantityInWindow(@Param("goodsId") Integer goodsId,
                                @Param("start") java.time.LocalDateTime start,
                                @Param("stop") java.time.LocalDateTime stop);
}
