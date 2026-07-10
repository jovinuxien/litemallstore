package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallCombination;

import java.util.List;

/**
 * Hand-written mapper for the combination campaign-definition table, mirroring
 * the style of {@link LitemallBargainMapper}.
 */
@Mapper
public interface LitemallCombinationMapper {

    @Insert("INSERT INTO litemall_combination(goods_id, title, pic_url, combination_price, original_price, required_members, limit_per_user, start_time, end_time, status, add_time, update_time, deleted) " +
            "VALUES(#{goodsId}, #{title}, #{picUrl}, #{combinationPrice}, #{originalPrice}, #{requiredMembers}, #{limitPerUser}, #{startTime}, #{endTime}, #{status}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallCombination combination);

    @Select("SELECT * FROM litemall_combination WHERE id = #{id} AND deleted = 0")
    LitemallCombination selectByPrimaryKey(Integer id);

    @Select("SELECT * FROM litemall_combination WHERE status = 1 AND deleted = 0 " +
            "AND (start_time IS NULL OR start_time <= NOW()) AND (end_time IS NULL OR end_time >= NOW()) " +
            "ORDER BY add_time DESC")
    List<LitemallCombination> selectActive();

    @Select("SELECT * FROM litemall_combination WHERE deleted = 0 ORDER BY add_time DESC")
    List<LitemallCombination> selectAll();

    @Update("<script>UPDATE litemall_combination" +
            "<set>" +
            "<if test='goodsId != null'>goods_id = #{goodsId},</if>" +
            "<if test='title != null'>title = #{title},</if>" +
            "<if test='picUrl != null'>pic_url = #{picUrl},</if>" +
            "<if test='combinationPrice != null'>combination_price = #{combinationPrice},</if>" +
            "<if test='originalPrice != null'>original_price = #{originalPrice},</if>" +
            "<if test='requiredMembers != null'>required_members = #{requiredMembers},</if>" +
            "<if test='limitPerUser != null'>limit_per_user = #{limitPerUser},</if>" +
            "<if test='startTime != null'>start_time = #{startTime},</if>" +
            "<if test='endTime != null'>end_time = #{endTime},</if>" +
            "<if test='status != null'>status = #{status},</if>" +
            "update_time = NOW()" +
            "</set>" +
            "WHERE id = #{id}</script>")
    int updateByPrimaryKeySelective(LitemallCombination combination);
}
