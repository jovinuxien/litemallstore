package org.linlinjava.litemall.goods.application.inventoryflow;

/**
 * One per-product message inside the inventory flow (the splitter's output).
 * The router fans these out by {@link Kind}.
 */
public record ProductFlowEvent(Kind kind, String pid) {

    public enum Kind {
        /** pid first seen (or resurrected) by this sync — deal-candidate scoring applies. */
        NEW_ARRIVAL,
        /** pid already known and still live — metrics refresh only. */
        UPDATED,
        /** pid gone from the CJ feed — availability drops to 0. */
        VANISHED
    }
}
