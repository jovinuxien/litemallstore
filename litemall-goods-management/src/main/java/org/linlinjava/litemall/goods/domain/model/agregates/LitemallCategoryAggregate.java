package org.linlinjava.litemall.goods.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.category.LitemallCategoryId;

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
