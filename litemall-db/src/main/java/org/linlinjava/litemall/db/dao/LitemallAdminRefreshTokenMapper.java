package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.*;
import org.linlinjava.litemall.db.domain.LitemallAdminRefreshToken;

@Mapper
public interface LitemallAdminRefreshTokenMapper {

    @Insert("INSERT INTO litemall_admin_refresh_token(admin_id, token_hash, login_type, expires_at, revoked, add_time, update_time, deleted) " +
            "VALUES(#{adminId}, #{tokenHash}, #{loginType}, #{expiresAt}, #{revoked}, #{addTime}, #{updateTime}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(LitemallAdminRefreshToken token);

    @Select("SELECT * FROM litemall_admin_refresh_token WHERE token_hash = #{tokenHash} AND deleted = 0")
    LitemallAdminRefreshToken selectByTokenHash(@Param("tokenHash") String tokenHash);

    @Update("UPDATE litemall_admin_refresh_token SET revoked = 1, update_time = NOW() WHERE token_hash = #{tokenHash} AND deleted = 0")
    int revokeByTokenHash(@Param("tokenHash") String tokenHash);

    @Update("UPDATE litemall_admin_refresh_token SET revoked = 1, update_time = NOW() WHERE admin_id = #{adminId} AND revoked = 0 AND deleted = 0")
    int revokeAllByAdminId(@Param("adminId") Integer adminId);
}
