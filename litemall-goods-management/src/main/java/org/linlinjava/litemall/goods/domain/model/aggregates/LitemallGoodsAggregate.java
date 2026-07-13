package org.linlinjava.litemall.goods.domain.model.aggregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallMoney;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.manufacturer.LitemallManufacturerId;


import java.time.LocalDateTime;

@Getter
@Setter
public class LitemallGoodsAggregate {

    private LitemallGoodsId goodsId;
    private String goodsSn;
    private String goodsName;
    private LitemallCategoryId categoryId;
    private LitemallManufacturerId manufacturerId;
    private String[] gallery;
    private String keyword; 
    private String brief;
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
    private String detail;
    /** Catalog origin: 'local' or 'cj' (CJ Dropshipping). Lets the storefront flag a line as CJ at checkout. */
    private String source;
    /** Freight-template binding (litemall_shipping_templates.id; 0/null = unbound → default template).
     *  Order's freight facade reads it off /srv/goods/goodsdetail (Wave-4 tempId handoff). */
    private Integer tempId;



}
