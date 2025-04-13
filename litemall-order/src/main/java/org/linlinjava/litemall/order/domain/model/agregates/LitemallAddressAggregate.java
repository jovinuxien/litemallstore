package org.linlinjava.litemall.order.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallAddressId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallUserId;

import java.time.LocalDateTime;


@Getter
@Setter
public class LitemallAddressAggregate {

    private LitemallAddressId addressId;
    private LitemallUserId userId;
    private String name;
    private String province;
    private String city;
    private String county;
    private String addressDetail;
    private String areaCode;
    private String postalCode;
    private String tel;
    private Boolean isDefault;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;
}
