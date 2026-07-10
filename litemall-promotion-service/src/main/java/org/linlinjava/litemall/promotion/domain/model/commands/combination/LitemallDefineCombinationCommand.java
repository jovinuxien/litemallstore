package org.linlinjava.litemall.promotion.domain.model.commands.combination;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Admin command to define a new combination (group-buy) campaign. Created in
 * DRAFT; a separate activate step makes it customer-visible.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LitemallDefineCombinationCommand {

    private Integer goodsId;
    private String title;
    private String picUrl;
    private BigDecimal combinationPrice;
    private BigDecimal originalPrice;
    private Integer requiredMembers;
    private Integer limitPerUser;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
