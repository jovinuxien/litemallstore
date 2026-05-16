package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class BargainSessionDtoResponse {

    private Integer bargainUserId;
    private Integer userId;
    private Integer bargainId;
    private BigDecimal currentPrice;
    private BigDecimal targetPrice;
    private String status;
    private Integer helpCount;
}
