package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallBargain;

import java.util.List;

@Mapper
public interface LitemallBargainMapper {

    @Insert("INSERT INTO litemall_bargain(goods_id, title, pic_url, unit, stock, sales, price, min_price, num, bargain_max_price, bargain_min_price, bargain_num, people_num, quota, is_postage, postage, temp_id, weight, volume, sort, status, is_del, start_time, stop_time, add_time, update_time, deleted) " +
            "VALUES(#{goodsId}, #{title}, #{picUrl}, #{unit}, #{stock}, #{sales}, #{price}, #{minPrice}, #{num}, #{bargainMaxPrice}, #{bargainMinPrice}, #{bargainNum}, #{peopleNum}, #{quota}, #{isPostage}, #{postage}, #{tempId}, #{weight}, #{volume}, #{sort}, #{status}, #{isDel}, #{startTime}, #{stopTime}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallBargain bargain);

    @Insert("INSERT INTO litemall_bargain(goods_id, title, pic_url, unit, stock, sales, price, min_price, num, bargain_max_price, bargain_min_price, bargain_num, people_num, quota, is_postage, postage, temp_id, weight, volume, sort, status, is_del, start_time, stop_time, add_time, update_time, deleted) " +
            "VALUES(#{goodsId}, #{title}, #{picUrl}, #{unit}, #{stock}, #{sales}, #{price}, #{minPrice}, #{num}, #{bargainMaxPrice}, #{bargainMinPrice}, #{bargainNum}, #{peopleNum}, #{quota}, #{isPostage}, #{postage}, #{tempId}, #{weight}, #{volume}, #{sort}, #{status}, #{isDel}, #{startTime}, #{stopTime}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LitemallBargain bargain);

    @Select("SELECT * FROM litemall_bargain WHERE id = #{id} AND deleted = 0")
    LitemallBargain selectByPrimaryKey(Integer id);

    @Select("SELECT * FROM litemall_bargain WHERE status = 1 AND is_del = 0 AND deleted = 0 AND start_time <= NOW() AND stop_time >= NOW() ORDER BY sort ASC")
    List<LitemallBargain> selectActive();

    @Update("UPDATE litemall_bargain SET deleted = 1, update_time = NOW() WHERE id = #{id}")
    int logicalDeleteByPrimaryKey(Integer id);

    @Update("<script>UPDATE litemall_bargain" +
            "<set>" +
            "<if test='title != null'>title = #{title},</if>" +
            "<if test='picUrl != null'>pic_url = #{picUrl},</if>" +
            "<if test='unit != null'>unit = #{unit},</if>" +
            "<if test='stock != null'>stock = #{stock},</if>" +
            "<if test='sales != null'>sales = #{sales},</if>" +
            "<if test='price != null'>price = #{price},</if>" +
            "<if test='minPrice != null'>min_price = #{minPrice},</if>" +
            "<if test='num != null'>num = #{num},</if>" +
            "<if test='bargainMaxPrice != null'>bargain_max_price = #{bargainMaxPrice},</if>" +
            "<if test='bargainMinPrice != null'>bargain_min_price = #{bargainMinPrice},</if>" +
            "<if test='bargainNum != null'>bargain_num = #{bargainNum},</if>" +
            "<if test='peopleNum != null'>people_num = #{peopleNum},</if>" +
            "<if test='quota != null'>quota = #{quota},</if>" +
            "<if test='isPostage != null'>is_postage = #{isPostage},</if>" +
            "<if test='postage != null'>postage = #{postage},</if>" +
            "<if test='sort != null'>sort = #{sort},</if>" +
            "<if test='status != null'>status = #{status},</if>" +
            "<if test='isDel != null'>is_del = #{isDel},</if>" +
            "<if test='startTime != null'>start_time = #{startTime},</if>" +
            "<if test='stopTime != null'>stop_time = #{stopTime},</if>" +
            "update_time = NOW()" +
            "</set>" +
            "WHERE id = #{id}</script>")
    int updateByPrimaryKeySelective(LitemallBargain bargain);
}
