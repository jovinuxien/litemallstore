package org.linlinjava.litemall.gatewayadmin.domain.model.aggregates.user;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.gatewayadmin.domain.valueobjects.user.LitemallRoleId;

import java.time.LocalDateTime;


@Setter
@Getter
public class LitemallRoleAggregate {

    private LitemallRoleId roleId;

    private String name;
    private String desc;
    private int enabled;


    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private boolean deleted;
}
