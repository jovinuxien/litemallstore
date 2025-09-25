package org.linlinjava.litemall.goods.domain.model.agregates;


import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsAttributeId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;

import java.time.LocalDateTime;

@Setter
@Getter
public class LitemallGoodsAttributeAggregate {

    private LitemallGoodsAttributeId goodsAttributeId;
    private LitemallGoodsId goodsId;
    private String attributeName;
    private String attributeValue;

    private LocalDateTime  addTime;
    private LocalDateTime updateTime;
    private boolean deleted;
}
