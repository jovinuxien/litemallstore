package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallUserRecharge;

import java.util.List;

@Mapper
public interface LitemallUserRechargeMapper {

    @Insert("INSERT INTO litemall_user_recharge(user_id, order_id, price, give_price, recharge_type, paid, pay_time, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{orderId}, #{price}, #{givePrice}, #{rechargeType}, #{paid}, #{payTime}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallUserRecharge recharge);

    @Insert("INSERT INTO litemall_user_recharge(user_id, order_id, price, give_price, recharge_type, paid, pay_time, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{orderId}, #{price}, #{givePrice}, #{rechargeType}, #{paid}, #{payTime}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LitemallUserRecharge recharge);

    @Select("SELECT * FROM litemall_user_recharge WHERE id = #{id} AND deleted = 0")
    LitemallUserRecharge selectByPrimaryKey(Integer id);

    @Select("SELECT * FROM litemall_user_recharge WHERE user_id = #{userId} AND deleted = 0 ORDER BY add_time DESC")
    List<LitemallUserRecharge> selectByUserId(Integer userId);

    @Update("UPDATE litemall_user_recharge SET deleted = 1, update_time = NOW() WHERE id = #{id}")
    int logicalDeleteByPrimaryKey(Integer id);

    @Update("<script>UPDATE litemall_user_recharge" +
            "<set>" +
            "<if test='orderId != null'>order_id = #{orderId},</if>" +
            "<if test='price != null'>price = #{price},</if>" +
            "<if test='givePrice != null'>give_price = #{givePrice},</if>" +
            "<if test='rechargeType != null'>recharge_type = #{rechargeType},</if>" +
            "<if test='paid != null'>paid = #{paid},</if>" +
            "<if test='payTime != null'>pay_time = #{payTime},</if>" +
            "update_time = NOW()" +
            "</set>" +
            "WHERE id = #{id}</script>")
    int updateByPrimaryKeySelective(LitemallUserRecharge recharge);
}
