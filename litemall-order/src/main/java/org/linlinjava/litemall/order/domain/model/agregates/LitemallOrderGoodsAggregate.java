package org.linlinjava.litemall.order.domain.model.agregates;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

import java.time.LocalDateTime;

@Setter
@Getter
public class LitemallOrderGoodsAggregate {

    private Integer orderGoodsId;
    private LitemallOrderId orderId;
    private LitemallGoodsId goodsId;
    private LitemallGoodsProductId productId;


    private String goodsName;
    private String goodsSn;
    private Short number;
    private LitemallMoney price;
    private String picUrl;
    private String[] specifications;
    private String comment;

    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private boolean delete;
}
