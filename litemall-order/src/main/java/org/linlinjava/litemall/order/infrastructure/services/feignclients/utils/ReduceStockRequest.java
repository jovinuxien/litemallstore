package org.linlinjava.litemall.order.infrastructure.services.feignclients.utils;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


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
