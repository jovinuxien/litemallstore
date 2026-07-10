package org.linlinjava.litemall.promotion.infrastructure.services.feignclients.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Wire shape of one customer's RFM statistics as returned by the litemall-order
 * stats endpoint. This is the <b>agreed contract</b> the promotion service codes
 * against; if the endpoint is not yet present in litemall-order it is raised as
 * a follow-up (see docs/phase2-targeting-pipeline.md) — promotion does not read
 * order tables directly.
 */
@Getter
@Setter
@NoArgsConstructor
public class CustomerRfmStatDto {

    private Integer userId;
    private LocalDateTime lastOrderAt;
    private Integer orderCount;
    private BigDecimal totalSpend;
}
