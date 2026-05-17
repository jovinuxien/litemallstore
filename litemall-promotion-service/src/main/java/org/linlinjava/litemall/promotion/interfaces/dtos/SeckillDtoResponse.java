package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class SeckillDtoResponse {

    private Integer seckillId;
    private String goodsName;
    private BigDecimal price;
    private Integer stock;
    private Integer sales;
    private Integer quota;
    private Byte timeSlot;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime stopTime;
}
