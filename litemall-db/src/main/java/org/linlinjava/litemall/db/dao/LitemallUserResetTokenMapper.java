package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallUserResetToken;

/** V37: password-reset tokens — same slim annotated pattern as {@link LitemallUserRefreshTokenMapper}. */
@Mapper
public interface LitemallUserResetTokenMapper {

    @Insert("INSERT INTO litemall_user_reset_token(user_id, token_hash, expires_at, used, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{tokenHash}, #{expiresAt}, #{used}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallUserResetToken token);

    @Select("SELECT * FROM litemall_user_reset_token WHERE token_hash = #{tokenHash} AND deleted = 0")
    LitemallUserResetToken selectByTokenHash(@Param("tokenHash") String tokenHash);

    /** Single-use consume: flips {@code used} only when still unused; returns 0 if already consumed (race-safe). */
    @Update("UPDATE litemall_user_reset_token SET used = 1, update_time = NOW() " +
            "WHERE token_hash = #{tokenHash} AND used = 0 AND deleted = 0")
    int consumeByTokenHash(@Param("tokenHash") String tokenHash);

    /** Invalidate every outstanding token for a user (on successful reset). */
    @Update("UPDATE litemall_user_reset_token SET used = 1, update_time = NOW() " +
            "WHERE user_id = #{userId} AND used = 0 AND deleted = 0")
    int invalidateAllByUserId(@Param("userId") Integer userId);
}
