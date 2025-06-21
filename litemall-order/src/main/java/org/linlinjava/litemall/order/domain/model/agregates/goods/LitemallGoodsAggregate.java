package org.linlinjava.litemall.order.domain.model.agregates.goods;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallCategoryId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallManufacturerId;

import java.time.LocalDateTime;

@Getter
@Setter
public class LitemallGoodsAggregate {

    private LitemallGoodsId goodsId;
    private LitemallCategoryId categoryId;
    private LitemallManufacturerId manufacturerId;


    private String goodsSn;
    private String goodsName;

    private String[] gallery;
    private String keyword; 
    private String brief;
    private String detail;

    private boolean isOnSale;
    private short sortOrder;

    private String picUrl;
    private String shareUrl;

    private boolean isHot;
    private boolean isNew;
    private String unit;

    private LitemallMoney counterPrice;
    private LitemallMoney retailPrice;

    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private boolean deleted;


}
