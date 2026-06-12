package org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Output of a CJ create-order call: the CJ order id/number and status. */
@Data
@AllArgsConstructor
public class CjOrderResult {
    private String cjOrderId;
    private String cjOrderNum;
    private String cjOrderStatus;
}
