package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallUserRefreshToken;

@Mapper
public interface LitemallUserRefreshTokenMapper {

    @Insert("INSERT INTO litemall_user_refresh_token(user_id, token_hash, login_type, expires_at, revoked, add_time, update_time, deleted) " +
            "VALUES(#{userId}, #{tokenHash}, #{loginType}, #{expiresAt}, #{revoked}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallUserRefreshToken token);

    @Select("SELECT * FROM litemall_user_refresh_token WHERE token_hash = #{tokenHash} AND deleted = 0")
    LitemallUserRefreshToken selectByTokenHash(@Param("tokenHash") String tokenHash);

    @Update("UPDATE litemall_user_refresh_token SET revoked = 1, update_time = NOW() WHERE token_hash = #{tokenHash} AND deleted = 0")
    int revokeByTokenHash(@Param("tokenHash") String tokenHash);

    @Update("UPDATE litemall_user_refresh_token SET revoked = 1, update_time = NOW() WHERE user_id = #{userId} AND revoked = 0 AND deleted = 0")
    int revokeAllByUserId(@Param("userId") Integer userId);
}
