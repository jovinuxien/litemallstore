package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallBargainUser;

import java.util.List;

@Mapper
public interface LitemallBargainUserMapper {

    @Insert("INSERT INTO litemall_bargain_user(user_id, bargain_id, bargain_price_min, bargain_price, status, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{bargainId}, #{bargainPriceMin}, #{bargainPrice}, #{status}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallBargainUser bargainUser);

    @Insert("INSERT INTO litemall_bargain_user(user_id, bargain_id, bargain_price_min, bargain_price, status, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{bargainId}, #{bargainPriceMin}, #{bargainPrice}, #{status}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LitemallBargainUser bargainUser);

    @Select("SELECT * FROM litemall_bargain_user WHERE id = #{id} AND deleted = 0")
    LitemallBargainUser selectByPrimaryKey(Integer id);

    @Select("SELECT * FROM litemall_bargain_user WHERE user_id = #{userId} AND deleted = 0 ORDER BY add_time DESC")
    List<LitemallBargainUser> selectByUserId(Integer userId);

    @Select("SELECT * FROM litemall_bargain_user WHERE user_id = #{userId} AND bargain_id = #{bargainId} AND deleted = 0 ORDER BY add_time DESC LIMIT 1")
    LitemallBargainUser selectByUserIdAndBargainId(@Param("userId") Integer userId, @Param("bargainId") Integer bargainId);

    @Update("UPDATE litemall_bargain_user SET deleted = 1, update_time = NOW() WHERE id = #{id}")
    int logicalDeleteByPrimaryKey(Integer id);

    @Update("<script>UPDATE litemall_bargain_user" +
            "<set>" +
            "<if test='bargainPriceMin != null'>bargain_price_min = #{bargainPriceMin},</if>" +
            "<if test='bargainPrice != null'>bargain_price = #{bargainPrice},</if>" +
            "<if test='status != null'>status = #{status},</if>" +
            "update_time = NOW()" +
            "</set>" +
            "WHERE id = #{id}</script>")
    int updateByPrimaryKeySelective(LitemallBargainUser bargainUser);
}
