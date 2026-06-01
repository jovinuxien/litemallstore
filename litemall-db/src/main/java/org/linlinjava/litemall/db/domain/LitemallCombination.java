package org.linlinjava.litemall.db.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Combination (group-buy / 拼团) campaign DEFINITION, owned by
 * litemall-promotion-service. The offer/rules side of group-buy; participation
 * (pink) remains litemall-order's {@code litemall_groupon}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LitemallCombination {

    private Integer       id;
    /** 关联商品ID */
    private Integer       goodsId;
    /** 拼团活动名称 */
    private String        title;
    /** 活动图片 */
    private String        picUrl;
    /** 拼团价 */
    private BigDecimal    combinationPrice;
    /** 原价(参考) */
    private BigDecimal    originalPrice;
    /** 成团所需人数 */
    private Integer       requiredMembers;
    /** 每人限购 0不限 */
    private Integer       limitPerUser;
    /** 活动开始时间 */
    private LocalDateTime startTime;
    /** 活动结束时间 */
    private LocalDateTime endTime;
    /** 状态 0草稿 1进行中 2已过期 3下架 */
    private Short         status;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean       deleted;
}
