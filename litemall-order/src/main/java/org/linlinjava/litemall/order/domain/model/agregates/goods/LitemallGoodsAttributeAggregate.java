package org.linlinjava.litemall.order.domain.model.agregates.goods;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsAttributeId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;

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
