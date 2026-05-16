package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallUserBill;

import java.util.List;

@Mapper
public interface LitemallUserBillMapper {

    @Insert("INSERT INTO litemall_user_bill(user_id, link_id, pm, title, category, type, number, balance, mark, status, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{linkId}, #{pm}, #{title}, #{category}, #{type}, #{number}, #{balance}, #{mark}, #{status}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallUserBill bill);

    @Insert("INSERT INTO litemall_user_bill(user_id, link_id, pm, title, category, type, number, balance, mark, status, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{linkId}, #{pm}, #{title}, #{category}, #{type}, #{number}, #{balance}, #{mark}, #{status}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LitemallUserBill bill);

    @Select("SELECT * FROM litemall_user_bill WHERE id = #{id} AND deleted = 0")
    LitemallUserBill selectByPrimaryKey(Integer id);

    @Select("SELECT * FROM litemall_user_bill WHERE user_id = #{userId} AND deleted = 0 ORDER BY add_time DESC")
    List<LitemallUserBill> selectByUserId(Integer userId);

    @Update("UPDATE litemall_user_bill SET deleted = 1, update_time = NOW() WHERE id = #{id}")
    int logicalDeleteByPrimaryKey(Integer id);

    @Update("<script>UPDATE litemall_user_bill" +
            "<set>" +
            "<if test='pm != null'>pm = #{pm},</if>" +
            "<if test='title != null'>title = #{title},</if>" +
            "<if test='category != null'>category = #{category},</if>" +
            "<if test='type != null'>type = #{type},</if>" +
            "<if test='number != null'>number = #{number},</if>" +
            "<if test='balance != null'>balance = #{balance},</if>" +
            "<if test='mark != null'>mark = #{mark},</if>" +
            "<if test='status != null'>status = #{status},</if>" +
            "update_time = NOW()" +
            "</set>" +
            "WHERE id = #{id}</script>")
    int updateByPrimaryKeySelective(LitemallUserBill bill);
}
