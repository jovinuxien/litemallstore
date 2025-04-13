package org.linlinjava.litemall.order.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponRulesId;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallGrouponStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Getter
@Setter
public class LitemallGrouponRulesAggregate {


    private LitemallGrouponRulesId grouponRulesId;
    private LitemallGoodsId goodsId;

    private String goodsName;
    private String picUrl;
    private BigDecimal discount;
    private Integer discountMember;
    private LitemallGrouponStatus status;

    private LocalDateTime expireTime;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private boolean deleted;
}
