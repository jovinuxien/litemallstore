package org.linlinjava.litemall.order.domain.model.agregates.goods;


import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsSpecificationId;

import java.time.LocalDateTime;

@Getter
@Setter
public class LitemallGoodsSpecificationAggregate {


    private LitemallGoodsSpecificationId goodsSpecificationId;
    private LitemallGoodsId goodsId;
    private String specifications;
    private String value;
    private String picUrl;

    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private boolean deleted;
}
