package org.linlinjava.litemall.order.domain.model.valueobjects.enums;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

public enum LitemallAfterSaleStatus {


    STATUS_INIT(0, "STATUS_INIT"),
    STATUS_REQUEST(1, "STATUS_REQUEST"),
    STATUS_RECEPT(2, "STATUS_RECEPT"),
    STATUS_REFUND(3, "STATUS_REFUND"),
    STATUS_REJECT(4, "STATUS_REJECT"),
    STATUS_CANCEL(5, "STATUS_CANCEL"),

    TYPE_GOODS_MISS(0, "TYPE_GOODS_MISS"),
    TYPE_GOODS_NEEDLESS(1, "TYPE_GOODS_NEEDLESS"),
    TYPE_GOODS_REQUIRED(2, "TYPE_GOODS_REQUIRED");


    private final Short code;
    private final String displayName;

    LitemallAfterSaleStatus(int code, String displayName) {
        this.code = (short) code;
        this.displayName = displayName;
    }

    public Short getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Map a persisted aftersale_status code back to its STATUS_* constant. The
     * TYPE_* constants share codes 0–2, so restrict the lookup to the STATUS_*
     * lifecycle values. Defaults to STATUS_INIT for an unknown code.
     */
    public static LitemallAfterSaleStatus fromStatusCode(short code) {
        for (LitemallAfterSaleStatus s : values()) {
            if (s.name().startsWith("STATUS_") && s.getCode() == code) {
                return s;
            }
        }
        return STATUS_INIT;
    }
}
