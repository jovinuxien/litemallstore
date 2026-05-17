package org.linlinjava.litemall.gatewayadmin.domain.model.aggregates.user;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.gatewayadmin.domain.valueobjects.user.LitemallAdminId;
import org.linlinjava.litemall.gatewayadmin.domain.valueobjects.user.LitemallRoleId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
public class LitemallAdminAggregate {

    private LitemallAdminId adminId;
    private List<LitemallRoleId> roleIds;


    private String username;
    private String password;
    private Byte gender;



    private LocalDate lastLoginTime;
    private String lastLoginIp;

    private String userLevel;

    private String avatar;

    private int status;

    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private boolean deleted;

}
