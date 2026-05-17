package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallUserLevel;

import java.util.List;

@Mapper
public interface LitemallUserLevelMapper {

    @Insert("INSERT INTO litemall_user_level(user_id, level_id, grade, experience, status, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{levelId}, #{grade}, #{experience}, #{status}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallUserLevel userLevel);

    @Insert("INSERT INTO litemall_user_level(user_id, level_id, grade, experience, status, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{levelId}, #{grade}, #{experience}, #{status}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LitemallUserLevel userLevel);

    @Select("SELECT * FROM litemall_user_level WHERE id = #{id} AND deleted = 0")
    LitemallUserLevel selectByPrimaryKey(Integer id);

    @Select("SELECT * FROM litemall_user_level WHERE user_id = #{userId} AND deleted = 0 ORDER BY add_time DESC")
    List<LitemallUserLevel> selectByUserId(Integer userId);

    @Select("SELECT * FROM litemall_user_level WHERE user_id = #{userId} AND status = 1 AND deleted = 0 ORDER BY grade DESC LIMIT 1")
    LitemallUserLevel selectCurrentByUserId(Integer userId);

    @Update("UPDATE litemall_user_level SET deleted = 1, update_time = NOW() WHERE id = #{id}")
    int logicalDeleteByPrimaryKey(Integer id);

    @Update("<script>UPDATE litemall_user_level" +
            "<set>" +
            "<if test='levelId != null'>level_id = #{levelId},</if>" +
            "<if test='grade != null'>grade = #{grade},</if>" +
            "<if test='experience != null'>experience = #{experience},</if>" +
            "<if test='status != null'>status = #{status},</if>" +
            "update_time = NOW()" +
            "</set>" +
            "WHERE id = #{id}</script>")
    int updateByPrimaryKeySelective(LitemallUserLevel userLevel);
}
