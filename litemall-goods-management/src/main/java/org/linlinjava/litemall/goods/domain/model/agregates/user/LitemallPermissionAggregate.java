package org.linlinjava.litemall.goods.domain.model.agregates.user;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.goods.domain.model.valueobjects.user.LitemallPermissionId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.user.LitemallRoleId;

import java.time.LocalDateTime;


@Setter
@Getter
public class LitemallPermissionAggregate {


    private LitemallPermissionId permissionId;
    private LitemallRoleId roleId;

    private String permission;

    private LocalDateTime addTime;
    private LocalDateTime updateTime;

    private boolean deleted;
}
