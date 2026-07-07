package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Admin-management view of a combination campaign definition.
 */
@Getter
@Setter
@Builder
public class CombinationManagerDtoResponse {

    private Integer combinationId;
    private Integer goodsId;
    private String title;
    private String picUrl;
    private BigDecimal combinationPrice;
    private BigDecimal originalPrice;
    private Integer requiredMembers;
    private Integer limitPerUser;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
