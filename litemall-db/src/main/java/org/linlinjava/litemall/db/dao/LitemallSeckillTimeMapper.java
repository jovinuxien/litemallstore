package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallSeckillTime;

import java.util.List;

@Mapper
public interface LitemallSeckillTimeMapper {

    @Insert("INSERT INTO litemall_seckill_time(time, status, add_time, update_time, deleted) " +
            "VALUES(#{time}, #{status}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallSeckillTime seckillTime);

    @Insert("INSERT INTO litemall_seckill_time(time, status, add_time, update_time, deleted) " +
            "VALUES(#{time}, #{status}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LitemallSeckillTime seckillTime);

    @Select("SELECT * FROM litemall_seckill_time WHERE id = #{id} AND deleted = 0")
    LitemallSeckillTime selectByPrimaryKey(Integer id);

    @Select("SELECT * FROM litemall_seckill_time WHERE status = 1 AND deleted = 0 ORDER BY time ASC")
    List<LitemallSeckillTime> selectEnabled();

    @Select("SELECT * FROM litemall_seckill_time WHERE time = #{hour} AND deleted = 0 LIMIT 1")
    LitemallSeckillTime selectByHour(Byte hour);

    @Update("UPDATE litemall_seckill_time SET deleted = 1, update_time = NOW() WHERE id = #{id}")
    int logicalDeleteByPrimaryKey(Integer id);

    @Update("<script>UPDATE litemall_seckill_time" +
            "<set>" +
            "<if test='time != null'>time = #{time},</if>" +
            "<if test='status != null'>status = #{status},</if>" +
            "update_time = NOW()" +
            "</set>" +
            "WHERE id = #{id}</script>")
    int updateByPrimaryKeySelective(LitemallSeckillTime seckillTime);
}
