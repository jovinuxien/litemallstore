package org.linlinjava.litemall.db.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Promotion campaign (algorithmic targeting) definition, owned by
 * litemall-promotion-service (Phase 2). Carries targeting criteria, the target
 * goods + a linked Phase-1 promo mechanic, a schedule, and a budget/cap. The
 * targeting engine evaluates the criteria against real customer statistics and
 * produces a promotion assignment.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LitemallPromotionCampaign {

    private Integer       id;
    /** 营销活动名称 */
    private String        name;
    /** 目标客户分群(CSV of segment names) */
    private String        targetSegments;
    /** R分下限 1-5 */
    private Short         minRecencyScore;
    /** F分下限 1-5 */
    private Short         minFrequencyScore;
    /** M分下限 1-5 */
    private Short         minMonetaryScore;
    /** 目标商品ID(CSV) */
    private String        targetGoodsIds;
    /** 关联促销类型 COUPON/SECKILL/BARGAIN/COMBINATION */
    private String        linkedPromotionType;
    /** 关联促销活动ID */
    private Integer       linkedPromotionId;
    /** 活动开始时间 */
    private LocalDateTime startTime;
    /** 活动结束时间 */
    private LocalDateTime endTime;
    /** 受众人数上限 null不限 */
    private Integer       maxAudience;
    /** 预算上限 null不限 */
    private BigDecimal    maxSpend;
    /** 最近一次评估命中的受众数 */
    private Integer       assignedCount;
    /** 已消耗预算 */
    private BigDecimal    spentBudget;
    /** 状态 0草稿 1进行中 2已完成 3暂停 */
    private Short         status;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean       deleted;
}
