package org.linlinjava.litemall.order.domain.model.agregates.goods;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;

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


    public boolean isStockEnough(short stockNumber) {
        return number >= stockNumber;
    }


}
