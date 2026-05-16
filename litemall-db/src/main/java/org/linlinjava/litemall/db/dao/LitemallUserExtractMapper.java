package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallUserExtract;

import java.util.List;

@Mapper
public interface LitemallUserExtractMapper {

    @Insert("INSERT INTO litemall_user_extract(user_id, real_name, extract_type, bank_code, bank_address, extract_price, balance, status, fail_msg, fail_time, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{realName}, #{extractType}, #{bankCode}, #{bankAddress}, #{extractPrice}, #{balance}, #{status}, #{failMsg}, #{failTime}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallUserExtract extract);

    @Insert("INSERT INTO litemall_user_extract(user_id, real_name, extract_type, bank_code, bank_address, extract_price, balance, status, fail_msg, fail_time, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{realName}, #{extractType}, #{bankCode}, #{bankAddress}, #{extractPrice}, #{balance}, #{status}, #{failMsg}, #{failTime}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LitemallUserExtract extract);

    @Select("SELECT * FROM litemall_user_extract WHERE id = #{id} AND deleted = 0")
    LitemallUserExtract selectByPrimaryKey(Integer id);

    @Select("SELECT * FROM litemall_user_extract WHERE user_id = #{userId} AND deleted = 0 ORDER BY add_time DESC")
    List<LitemallUserExtract> selectByUserId(Integer userId);

    @Update("UPDATE litemall_user_extract SET deleted = 1, update_time = NOW() WHERE id = #{id}")
    int logicalDeleteByPrimaryKey(Integer id);

    @Update("<script>UPDATE litemall_user_extract" +
            "<set>" +
            "<if test='realName != null'>real_name = #{realName},</if>" +
            "<if test='extractType != null'>extract_type = #{extractType},</if>" +
            "<if test='bankCode != null'>bank_code = #{bankCode},</if>" +
            "<if test='bankAddress != null'>bank_address = #{bankAddress},</if>" +
            "<if test='extractPrice != null'>extract_price = #{extractPrice},</if>" +
            "<if test='balance != null'>balance = #{balance},</if>" +
            "<if test='status != null'>status = #{status},</if>" +
            "<if test='failMsg != null'>fail_msg = #{failMsg},</if>" +
            "<if test='failTime != null'>fail_time = #{failTime},</if>" +
            "update_time = NOW()" +
            "</set>" +
            "WHERE id = #{id}</script>")
    int updateByPrimaryKeySelective(LitemallUserExtract extract);
}