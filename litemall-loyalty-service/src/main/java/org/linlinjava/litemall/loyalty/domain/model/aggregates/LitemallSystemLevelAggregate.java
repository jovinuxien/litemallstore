package org.linlinjava.litemall.loyalty.domain.model.aggregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallSystemLevelId;

import java.math.BigDecimal;

@Getter
@Setter
public class LitemallSystemLevelAggregate {

    private LitemallSystemLevelId systemLevelId;
    private String name;
    private Byte level;
    private Integer requiredExperience;
    private BigDecimal discount; // 100 = no discount
    private String icon;
}
