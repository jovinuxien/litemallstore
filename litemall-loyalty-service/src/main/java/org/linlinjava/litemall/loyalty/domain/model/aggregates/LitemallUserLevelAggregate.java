package org.linlinjava.litemall.loyalty.domain.model.aggregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserLevelRecordId;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserId;

import java.time.LocalDateTime;

@Getter
@Setter
public class LitemallUserLevelAggregate {

    private LitemallUserLevelRecordId levelRecordId;
    private LitemallUserId userId;
    private Integer levelId;
    private Byte grade;
    private Integer experience;
    private Byte status;
    private LitemallSystemLevelAggregate levelDefinition;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
}
