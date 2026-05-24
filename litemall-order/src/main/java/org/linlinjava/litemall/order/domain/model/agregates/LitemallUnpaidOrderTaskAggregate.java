package org.linlinjava.litemall.order.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

import java.time.LocalDateTime;

@Getter
@Setter
public class LitemallUnpaidOrderTaskAggregate {

    private LitemallOrderId orderId;
    private LocalDateTime dueAt;
    private LocalDateTime createdAt;
}
