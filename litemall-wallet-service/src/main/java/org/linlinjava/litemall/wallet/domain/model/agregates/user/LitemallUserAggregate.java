package org.linlinjava.litemall.wallet.domain.model.agregates.user;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.user.LitemallUserId;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class LitemallUserAggregate {

    private LitemallUserId userId;
    private String username;
    private String nickname;
    private String mobile;
    private String avatar;
    private Byte gender;
    private LocalDate birthday;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private Byte userLevel;
    private String weixinOpenid;
    private String sessionKey;
    private Byte status;

    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;
}
