package org.linlinjava.litemall.goods.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsProductId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallMoney;


import java.time.LocalDateTime;
import java.util.List;


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


    public boolean isStockEnough(short stockNumber) {
        return number >= stockNumber;
    }

}
