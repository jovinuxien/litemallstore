package org.linlinjava.litemall.order.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGoodsProductId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;

import java.time.LocalDateTime;


@Getter
@Setter
public class LitemallGoodsProductAggregate {

    private LitemallGoodsProductId goodsProductId;
    private LitemallGoodsId goodsId;
    
    private String[] specification;
    private LitemallMoney price;
    private Integer number;
    private String url;

    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private boolean deleted;
}
