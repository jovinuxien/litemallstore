package org.linlinjava.litemall.goods.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.goods.domain.model.valueobjects.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.manufacturer.LitemallManufacturerId;


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
