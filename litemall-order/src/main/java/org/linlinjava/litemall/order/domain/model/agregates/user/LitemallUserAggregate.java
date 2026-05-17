package org.linlinjava.litemall.order.domain.model.agregates.user;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Getter
@Setter
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
