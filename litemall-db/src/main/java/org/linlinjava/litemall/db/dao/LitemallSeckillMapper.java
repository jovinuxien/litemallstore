package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallSeckill;

import java.util.List;

@Mapper
public interface LitemallSeckillMapper {

    @Insert("INSERT INTO litemall_seckill(goods_id, goods_name, pic_url, price, cost, stock, sales, quota, quota_show, time, is_postage, postage, temp_id, weight, volume, sort, status, is_del, start_time, stop_time, add_time, update_time, deleted) " +
            "VALUES(#{goodsId}, #{goodsName}, #{picUrl}, #{price}, #{cost}, #{stock}, #{sales}, #{quota}, #{quotaShow}, #{time}, #{isPostage}, #{postage}, #{tempId}, #{weight}, #{volume}, #{sort}, #{status}, #{isDel}, #{startTime}, #{stopTime}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallSeckill seckill);

    @Insert("INSERT INTO litemall_seckill(goods_id, goods_name, pic_url, price, cost, stock, sales, quota, quota_show, time, is_postage, postage, temp_id, weight, volume, sort, status, is_del, start_time, stop_time, add_time, update_time, deleted) " +
            "VALUES(#{goodsId}, #{goodsName}, #{picUrl}, #{price}, #{cost}, #{stock}, #{sales}, #{quota}, #{quotaShow}, #{time}, #{isPostage}, #{postage}, #{tempId}, #{weight}, #{volume}, #{sort}, #{status}, #{isDel}, #{startTime}, #{stopTime}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LitemallSeckill seckill);

    @Select("SELECT * FROM litemall_seckill WHERE id = #{id} AND deleted = 0")
    LitemallSeckill selectByPrimaryKey(Integer id);

    @Select("SELECT * FROM litemall_seckill WHERE status = 1 AND is_del = 0 AND deleted = 0 AND start_time <= NOW() AND stop_time >= NOW() ORDER BY sort ASC")
    List<LitemallSeckill> selectActive();

    @Update("UPDATE litemall_seckill SET deleted = 1, update_time = NOW() WHERE id = #{id}")
    int logicalDeleteByPrimaryKey(Integer id);

    @Update("UPDATE litemall_seckill SET stock = stock - #{quantity}, sales = sales + #{quantity}, update_time = NOW() WHERE id = #{id} AND stock >= #{quantity}")
    int decreaseStock(@Param("id") Integer id, @Param("quantity") Integer quantity);

    @Update("<script>UPDATE litemall_seckill" +
            "<set>" +
            "<if test='goodsName != null'>goods_name = #{goodsName},</if>" +
            "<if test='picUrl != null'>pic_url = #{picUrl},</if>" +
            "<if test='price != null'>price = #{price},</if>" +
            "<if test='cost != null'>cost = #{cost},</if>" +
            "<if test='stock != null'>stock = #{stock},</if>" +
            "<if test='sales != null'>sales = #{sales},</if>" +
            "<if test='quota != null'>quota = #{quota},</if>" +
            "<if test='quotaShow != null'>quota_show = #{quotaShow},</if>" +
            "<if test='time != null'>time = #{time},</if>" +
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
    int updateByPrimaryKeySelective(LitemallSeckill seckill);
}
