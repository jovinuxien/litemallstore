package org.linlinjava.litemall.goods.domain.model.aggregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallMoney;


import java.time.LocalDateTime;


@Getter
@Setter
public class LitemallGoodsProductAggregate {

    private LitemallGoodsProductId goodsProductId;
    private LitemallGoodsId goodsId;
    
    private String[] specifications;
    private LitemallMoney price;
    private Integer number;
    private String url;

    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private boolean deleted;


    public boolean isStockEnough(short stockNumber) {
        return number >= stockNumber;
    }

}
