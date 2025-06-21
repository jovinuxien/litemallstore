package org.linlinjava.litemall.order.domain.model.agregates.goods;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallManufacturerId;

import java.time.LocalDateTime;


@Getter
@Setter
public class LitemallManufacturerAggregate {

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
