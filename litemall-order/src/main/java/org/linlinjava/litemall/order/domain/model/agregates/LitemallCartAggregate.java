package org.linlinjava.litemall.order.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class LitemallCartAggregate {


    private LitemallCartId cartId;
    private LitemallUserId userId;
    private LitemallGoodsId goodsId;
    private LitemallGoodsProductId productId;

    private String goodsSn;
    private String goodsName;
    private LitemallMoney price;
    private Integer number;
    private String[] specifications;
    private boolean checked;
    private String picUrl;
    
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private boolean deleted;


}
