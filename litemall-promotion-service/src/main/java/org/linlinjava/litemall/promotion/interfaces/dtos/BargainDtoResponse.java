package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class BargainDtoResponse {

    private Integer bargainId;
    private String title;
    private BigDecimal price;
    private BigDecimal minPrice;
    private Integer stock;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime stopTime;
    private Integer bargainNum;
    private Integer peopleNum;
}
