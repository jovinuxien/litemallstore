package org.linlinjava.litemall.goods.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.goods.domain.model.valueobjects.manufacturer.LitemallManufacturerId;

import java.time.LocalDateTime;


@Setter
@Getter
public class LitemallBrandAggregate {

    private LitemallManufacturerId brandId;

    private String name;
    private String description;
    private String picUrl;
    private Integer sortOrder;

    private Double floorPrice;

    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private boolean deleted;
}
