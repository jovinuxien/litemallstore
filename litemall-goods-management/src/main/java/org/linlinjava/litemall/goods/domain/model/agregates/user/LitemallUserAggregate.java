package org.linlinjava.litemall.goods.domain.model.agregates.user;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.user.LitemallUserId;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Setter
@Getter
public class LitemallUserAggregate {


    private LitemallUserId userId;
    private String username;
    private String password;
    private Byte gender;
    private LocalDate birthday;
    private LocalDate lastLoginTime;
    private String lastLoginIp;
    private String userLevel;
    private String avatar;
    private String sessionKey;
    private String weixinOpenid;
    private boolean isAdmin;
    private int status;

    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private boolean deleted;




}
