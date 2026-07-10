package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallPromotionCampaign;

import java.util.List;

/**
 * Hand-written mapper for the promotion campaign (algorithmic targeting) table,
 * mirroring the style of {@link LitemallCombinationMapper}.
 */
@Mapper
public interface LitemallPromotionCampaignMapper {

    @Insert("INSERT INTO litemall_promotion_campaign(name, target_segments, min_recency_score, min_frequency_score, min_monetary_score, " +
            "target_goods_ids, linked_promotion_type, linked_promotion_id, start_time, end_time, max_audience, max_spend, " +
            "assigned_count, spent_budget, status, add_time, update_time, deleted) " +
            "VALUES(#{name}, #{targetSegments}, #{minRecencyScore}, #{minFrequencyScore}, #{minMonetaryScore}, " +
            "#{targetGoodsIds}, #{linkedPromotionType}, #{linkedPromotionId}, #{startTime}, #{endTime}, #{maxAudience}, #{maxSpend}, " +
            "#{assignedCount}, #{spentBudget}, #{status}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallPromotionCampaign campaign);

    @Select("SELECT * FROM litemall_promotion_campaign WHERE id = #{id} AND deleted = 0")
    LitemallPromotionCampaign selectByPrimaryKey(Integer id);

    @Select("SELECT * FROM litemall_promotion_campaign WHERE status = 1 AND deleted = 0 ORDER BY add_time DESC")
    List<LitemallPromotionCampaign> selectActive();

    @Select("SELECT * FROM litemall_promotion_campaign WHERE deleted = 0 ORDER BY add_time DESC")
    List<LitemallPromotionCampaign> selectAll();

    @Update("<script>UPDATE litemall_promotion_campaign" +
            "<set>" +
            "<if test='name != null'>name = #{name},</if>" +
            "<if test='targetSegments != null'>target_segments = #{targetSegments},</if>" +
            "<if test='minRecencyScore != null'>min_recency_score = #{minRecencyScore},</if>" +
            "<if test='minFrequencyScore != null'>min_frequency_score = #{minFrequencyScore},</if>" +
            "<if test='minMonetaryScore != null'>min_monetary_score = #{minMonetaryScore},</if>" +
            "<if test='targetGoodsIds != null'>target_goods_ids = #{targetGoodsIds},</if>" +
            "<if test='linkedPromotionType != null'>linked_promotion_type = #{linkedPromotionType},</if>" +
            "<if test='linkedPromotionId != null'>linked_promotion_id = #{linkedPromotionId},</if>" +
            "<if test='startTime != null'>start_time = #{startTime},</if>" +
            "<if test='endTime != null'>end_time = #{endTime},</if>" +
            "<if test='maxAudience != null'>max_audience = #{maxAudience},</if>" +
            "<if test='maxSpend != null'>max_spend = #{maxSpend},</if>" +
            "<if test='assignedCount != null'>assigned_count = #{assignedCount},</if>" +
            "<if test='spentBudget != null'>spent_budget = #{spentBudget},</if>" +
            "<if test='status != null'>status = #{status},</if>" +
            "update_time = NOW()" +
            "</set>" +
            "WHERE id = #{id}</script>")
    int updateByPrimaryKeySelective(LitemallPromotionCampaign campaign);
}
