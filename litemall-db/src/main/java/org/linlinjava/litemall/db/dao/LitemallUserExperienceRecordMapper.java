package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallUserExperienceRecord;

import java.util.List;

@Mapper
public interface LitemallUserExperienceRecordMapper {

    @Insert("INSERT INTO litemall_user_experience_record(user_id, link_id, link_type, title, experience, balance, mark, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{linkId}, #{linkType}, #{title}, #{experience}, #{balance}, #{mark}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallUserExperienceRecord record);

    @Insert("INSERT INTO litemall_user_experience_record(user_id, link_id, link_type, title, experience, balance, mark, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{linkId}, #{linkType}, #{title}, #{experience}, #{balance}, #{mark}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LitemallUserExperienceRecord record);

    @Select("SELECT * FROM litemall_user_experience_record WHERE id = #{id} AND deleted = 0")
    LitemallUserExperienceRecord selectByPrimaryKey(Integer id);

    @Select("SELECT * FROM litemall_user_experience_record WHERE user_id = #{userId} AND deleted = 0 ORDER BY add_time DESC")
    List<LitemallUserExperienceRecord> selectByUserId(Integer userId);

    @Update("UPDATE litemall_user_experience_record SET deleted = 1, update_time = NOW() WHERE id = #{id}")
    int logicalDeleteByPrimaryKey(Integer id);

    @Update("<script>UPDATE litemall_user_experience_record" +
            "<set>" +
            "<if test='title != null'>title = #{title},</if>" +
            "<if test='experience != null'>experience = #{experience},</if>" +
            "<if test='balance != null'>balance = #{balance},</if>" +
            "<if test='mark != null'>mark = #{mark},</if>" +
            "update_time = NOW()" +
            "</set>" +
            "WHERE id = #{id}</script>")
    int updateByPrimaryKeySelective(LitemallUserExperienceRecord record);
}
