package org.linlinjava.litemall.db.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LitemallUserRefreshToken {

    private Integer       id;
    /** 用户ID */
    private Integer       userId;
    /** 刷新令牌的SHA-256哈希(不存明文) */
    private String        tokenHash;
    /** 登录类型 h5/wechat/mobile */
    private String        loginType;
    /** 刷新令牌过期时间 */
    private LocalDateTime expiresAt;
    /** 0有效 1已吊销 */
    private Boolean       revoked;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean       deleted;
}
