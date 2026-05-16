package org.linlinjava.litemall.order.domain.model.agregates.goods;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallCategoryId;

import java.time.LocalDateTime;


@Getter
@Setter
public class LitemallCategoryAggregate {

    private LitemallCategoryId categoryId;

    private String categoryName;
    private String keywords;
    private String description;
    private Integer parentId;
    private String iconUrl;
    private String picUrl;
    private String level;
    private Integer sortOrder;


    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private boolean deleted;
}
