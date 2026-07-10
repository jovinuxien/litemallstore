package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Customer-facing view of a combination (group-buy) campaign definition.
 */
@Getter
@Setter
@Builder
public class CombinationDtoResponse {

    private Integer combinationId;
    private Integer goodsId;
    private String title;
    private String picUrl;
    private BigDecimal combinationPrice;
    private BigDecimal originalPrice;
    private Integer requiredMembers;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
