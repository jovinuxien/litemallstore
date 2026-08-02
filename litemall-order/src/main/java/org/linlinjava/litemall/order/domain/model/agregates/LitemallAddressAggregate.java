package org.linlinjava.litemall.order.domain.model.agregates;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallAddressId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

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
    /** ISO-3166 alpha-2 destination country (V48; null on legacy rows). */
    private String countryCode;
    private String tel;
    private Boolean isDefault;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;
}
