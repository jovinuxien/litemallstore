package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallBargainHelp;

import java.util.List;

@Mapper
public interface LitemallBargainHelpMapper {

    @Insert("INSERT INTO litemall_bargain_help(user_id, bargain_id, bargain_user_id, price, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{bargainId}, #{bargainUserId}, #{price}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallBargainHelp bargainHelp);

    @Insert("INSERT INTO litemall_bargain_help(user_id, bargain_id, bargain_user_id, price, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{bargainId}, #{bargainUserId}, #{price}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LitemallBargainHelp bargainHelp);

    @Select("SELECT * FROM litemall_bargain_help WHERE id = #{id} AND deleted = 0")
    LitemallBargainHelp selectByPrimaryKey(Integer id);

    @Select("SELECT * FROM litemall_bargain_help WHERE user_id = #{userId} AND deleted = 0 ORDER BY add_time DESC")
    List<LitemallBargainHelp> selectByUserId(Integer userId);

    @Select("SELECT * FROM litemall_bargain_help WHERE bargain_user_id = #{bargainUserId} AND deleted = 0 ORDER BY add_time DESC")
    List<LitemallBargainHelp> selectByBargainUserId(Integer bargainUserId);

    @Select("SELECT COUNT(*) FROM litemall_bargain_help WHERE bargain_user_id = #{bargainUserId} AND deleted = 0")
    int countByBargainUserId(Integer bargainUserId);

    @Update("UPDATE litemall_bargain_help SET deleted = 1, update_time = NOW() WHERE id = #{id}")
    int logicalDeleteByPrimaryKey(Integer id);

    @Update("<script>UPDATE litemall_bargain_help" +
            "<set>" +
            "<if test='price != null'>price = #{price},</if>" +
            "update_time = NOW()" +
            "</set>" +
            "WHERE id = #{id}</script>")
    int updateByPrimaryKeySelective(LitemallBargainHelp bargainHelp);
}
