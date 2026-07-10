package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin-management view of an algorithmic-targeting campaign definition.
 */
@Getter
@Setter
@Builder
public class CampaignManagerDtoResponse {

    private Integer campaignId;
    private String name;
    private List<String> targetSegments;
    private Integer minRecencyScore;
    private Integer minFrequencyScore;
    private Integer minMonetaryScore;
    private List<Integer> targetGoodsIds;
    private String linkedPromotionType;
    private Integer linkedPromotionId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxAudience;
    private BigDecimal maxSpend;
    private Integer assignedCount;
    private BigDecimal spentBudget;
    private String status;
}
