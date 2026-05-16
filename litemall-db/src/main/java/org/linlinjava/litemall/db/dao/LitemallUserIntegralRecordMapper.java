package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallUserIntegralRecord;

import java.util.List;

@Mapper
public interface LitemallUserIntegralRecordMapper {

    @Insert("INSERT INTO litemall_user_integral_record(user_id, link_id, link_type, title, number, balance, mark, status, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{linkId}, #{linkType}, #{title}, #{number}, #{balance}, #{mark}, #{status}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallUserIntegralRecord record);

    @Insert("INSERT INTO litemall_user_integral_record(user_id, link_id, link_type, title, number, balance, mark, status, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{linkId}, #{linkType}, #{title}, #{number}, #{balance}, #{mark}, #{status}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LitemallUserIntegralRecord record);

    @Select("SELECT * FROM litemall_user_integral_record WHERE id = #{id} AND deleted = 0")
    LitemallUserIntegralRecord selectByPrimaryKey(Integer id);

    @Select("SELECT * FROM litemall_user_integral_record WHERE user_id = #{userId} AND deleted = 0 ORDER BY add_time DESC")
    List<LitemallUserIntegralRecord> selectByUserId(Integer userId);

    @Select("SELECT COALESCE(SUM(number), 0) FROM litemall_user_integral_record WHERE user_id = #{userId} AND status = 1 AND deleted = 0")
    Integer sumNumberByUserId(Integer userId);

    @Update("UPDATE litemall_user_integral_record SET deleted = 1, update_time = NOW() WHERE id = #{id}")
    int logicalDeleteByPrimaryKey(Integer id);

    @Update("<script>UPDATE litemall_user_integral_record" +
            "<set>" +
            "<if test='title != null'>title = #{title},</if>" +
            "<if test='number != null'>number = #{number},</if>" +
            "<if test='balance != null'>balance = #{balance},</if>" +
            "<if test='mark != null'>mark = #{mark},</if>" +
            "<if test='status != null'>status = #{status},</if>" +
            "update_time = NOW()" +
            "</set>" +
            "WHERE id = #{id}</script>")
    int updateByPrimaryKeySelective(LitemallUserIntegralRecord record);
}
