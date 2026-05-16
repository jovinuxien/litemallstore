package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallSystemUserLevel;

import java.util.List;

@Mapper
public interface LitemallSystemUserLevelMapper {

    @Insert("INSERT INTO litemall_system_user_level(name, level, experience, discount, icon, is_show, add_time, update_time, deleted) " +
            "VALUES(#{name}, #{level}, #{experience}, #{discount}, #{icon}, #{isShow}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallSystemUserLevel level);

    @Insert("INSERT INTO litemall_system_user_level(name, level, experience, discount, icon, is_show, add_time, update_time, deleted) " +
            "VALUES(#{name}, #{level}, #{experience}, #{discount}, #{icon}, #{isShow}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LitemallSystemUserLevel level);

    @Select("SELECT * FROM litemall_system_user_level WHERE id = #{id} AND deleted = 0")
    LitemallSystemUserLevel selectByPrimaryKey(Integer id);

    @Select("SELECT * FROM litemall_system_user_level WHERE deleted = 0 ORDER BY level ASC")
    List<LitemallSystemUserLevel> selectAll();

    @Select("SELECT * FROM litemall_system_user_level WHERE level = #{level} AND deleted = 0 LIMIT 1")
    LitemallSystemUserLevel selectByLevel(Byte level);

    @Select("SELECT * FROM litemall_system_user_level WHERE level > #{level} AND deleted = 0 ORDER BY level ASC LIMIT 1")
    LitemallSystemUserLevel selectNextLevel(Byte level);

    @Update("UPDATE litemall_system_user_level SET deleted = 1, update_time = NOW() WHERE id = #{id}")
    int logicalDeleteByPrimaryKey(Integer id);

    @Update("<script>UPDATE litemall_system_user_level" +
            "<set>" +
            "<if test='name != null'>name = #{name},</if>" +
            "<if test='level != null'>level = #{level},</if>" +
            "<if test='experience != null'>experience = #{experience},</if>" +
            "<if test='discount != null'>discount = #{discount},</if>" +
            "<if test='icon != null'>icon = #{icon},</if>" +
            "<if test='isShow != null'>is_show = #{isShow},</if>" +
            "update_time = NOW()" +
            "</set>" +
            "WHERE id = #{id}</script>")
    int updateByPrimaryKeySelective(LitemallSystemUserLevel level);
}
