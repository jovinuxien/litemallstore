package org.linlinjava.litemall.promotion.domain.model.commands.campaign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin command to define a new algorithmic-targeting campaign. Created in
 * DRAFT; a separate activate step makes its assignments deliver. Carries the
 * raw targeting inputs (segment names + optional RFM score floors), the target
 * goods + linked Phase-1 mechanic, schedule, and budget/cap. Primitive types
 * here; the application service maps them to domain value objects.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LitemallDefineCampaignCommand {

    private String name;
    /** Target segment names (e.g. CHAMPIONS, AT_RISK); empty = any segment. */
    private List<String> targetSegments;
    private Integer minRecencyScore;
    private Integer minFrequencyScore;
    private Integer minMonetaryScore;
    private List<Integer> targetGoodsIds;
    /** COUPON / SECKILL / BARGAIN / COMBINATION / NONE. */
    private String linkedPromotionType;
    private Integer linkedPromotionId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** Budget/cap (Katsov §3.6.2); null = uncapped. */
    private Integer maxAudience;
    private BigDecimal maxSpend;
}
