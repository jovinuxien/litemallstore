package org.linlinjava.litemall.goods.application.inventoryflow;

import java.math.BigDecimal;

/**
 * Content-enriched view of a {@link ProductFlowEvent}: the event joined with the promoted
 * goods row's pricing/stock/signal state. {@code goodsId} is null when the pid has no
 * (live) promoted goods yet — downstream activators must skip, never fabricate.
 * {@code cost}/{@code marginPct} are null while the wholesale cost is not captured.
 */
public record ProductInventoryContext(ProductFlowEvent event,
                                      Integer goodsId,
                                      BigDecimal retail,
                                      BigDecimal cost,
                                      BigDecimal marginPct,
                                      int stockTotal,
                                      BigDecimal rating,
                                      Integer reviewCount) {

    public static ProductInventoryContext unresolved(ProductFlowEvent event) {
        return new ProductInventoryContext(event, null, null, null, null, 0, null, null);
    }
}
