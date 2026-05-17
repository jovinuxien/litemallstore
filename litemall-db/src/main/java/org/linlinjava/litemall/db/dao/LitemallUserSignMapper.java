package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallUserSign;

import java.util.List;

@Mapper
public interface LitemallUserSignMapper {

    @Insert("INSERT INTO litemall_user_sign(user_id, integral, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{integral}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallUserSign sign);

    @Insert("INSERT INTO litemall_user_sign(user_id, integral, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{integral}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LitemallUserSign sign);

    @Select("SELECT * FROM litemall_user_sign WHERE id = #{id} AND deleted = 0")
    LitemallUserSign selectByPrimaryKey(Integer id);

    @Select("SELECT * FROM litemall_user_sign WHERE user_id = #{userId} AND deleted = 0 ORDER BY add_time DESC")
    List<LitemallUserSign> selectByUserId(Integer userId);

    @Select("SELECT COUNT(*) FROM litemall_user_sign WHERE user_id = #{userId} AND DATE(add_time) = CURDATE() AND deleted = 0")
    int countTodayByUserId(Integer userId);

    @Update("UPDATE litemall_user_sign SET deleted = 1, update_time = NOW() WHERE id = #{id}")
    int logicalDeleteByPrimaryKey(Integer id);

    @Update("<script>UPDATE litemall_user_sign" +
            "<set>" +
            "<if test='integral != null'>integral = #{integral},</if>" +
            "update_time = NOW()" +
            "</set>" +
            "WHERE id = #{id}</script>")
    int updateByPrimaryKeySelective(LitemallUserSign sign);
}
