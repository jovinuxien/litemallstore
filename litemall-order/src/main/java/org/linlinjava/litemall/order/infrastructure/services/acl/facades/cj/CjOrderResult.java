package org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Output of a CJ create-order call: the CJ order id/number/status and the logistics line used. */
@Data
@AllArgsConstructor
public class CjOrderResult {
    private String cjOrderId;
    private String cjOrderNum;
    private String cjOrderStatus;
    /** The logistics line the order was placed with (persisted as the order's ship channel). */
    private String logisticName;
}
