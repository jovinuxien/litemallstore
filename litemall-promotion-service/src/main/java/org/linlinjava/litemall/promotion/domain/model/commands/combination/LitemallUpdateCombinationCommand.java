package org.linlinjava.litemall.promotion.domain.model.commands.combination;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Admin command to update an existing combination campaign definition.
 * {@code null} fields are left unchanged. Running groups are unaffected —
 * they carry start-time snapshots of headcount and deadline.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LitemallUpdateCombinationCommand {

    private String title;
    private String picUrl;
    private BigDecimal combinationPrice;
    private BigDecimal originalPrice;
    private Integer requiredMembers;
    private Integer limitPerUser;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
