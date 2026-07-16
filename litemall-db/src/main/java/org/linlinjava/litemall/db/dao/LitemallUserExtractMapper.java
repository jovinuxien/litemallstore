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

    // ------------------------------------------------------------------
    // Wave 5 affiliate — admin extract console. Status codes: -1 rejected,
    // 0 pending, 1 processing, 2 completed. The approve/reject transitions
    // are GUARDED on status = 0 so a double-click or a raced second admin
    // surfaces as a 0-row update, never a double refund.
    // ------------------------------------------------------------------

    @Select("<script>SELECT * FROM litemall_user_extract WHERE deleted = 0" +
            "<if test='status != null'> AND status = #{status}</if>" +
            " ORDER BY add_time DESC, id DESC LIMIT #{offset}, #{limit}</script>")
    List<LitemallUserExtract> selectAdminPage(@Param("status") Byte status,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    @Select("<script>SELECT COUNT(*) FROM litemall_user_extract WHERE deleted = 0" +
            "<if test='status != null'> AND status = #{status}</if></script>")
    long countAdmin(@Param("status") Byte status);

    @Update("UPDATE litemall_user_extract SET status = 2, update_time = NOW() " +
            "WHERE id = #{id} AND status = 0 AND deleted = 0")
    int approveFromPending(@Param("id") Integer id);

    @Update("UPDATE litemall_user_extract SET status = -1, fail_msg = #{failMsg}, fail_time = NOW(), update_time = NOW() " +
            "WHERE id = #{id} AND status = 0 AND deleted = 0")
    int rejectFromPending(@Param("id") Integer id, @Param("failMsg") String failMsg);
}