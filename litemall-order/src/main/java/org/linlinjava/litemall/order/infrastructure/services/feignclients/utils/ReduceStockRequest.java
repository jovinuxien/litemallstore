package org.linlinjava.litemall.order.infrastructure.services.feignclients.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReduceStockRequest {

    @NotNull
    private Integer productId;

    @NotNull
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer number;
}
