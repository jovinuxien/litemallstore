package org.linlinjava.litemall.order.interfaces.util;

/**
 * Wave-5 affiliate errno family (660–669), beside the existing per-vertical
 * constants (CJ dispute 720, aftersale 730, flash deals 650–652, …). Shared by
 * {@code LitemallAffiliateRestController} and {@code LitemallAdminExtractController}
 * so the codes are minted exactly once; the full table is documented in
 * {@code docs/handoff-affiliate-portal.md}.
 */
public final class AffiliateErrno {

    /** Caller is not a live promoter ({@code is_promoter=1}, not deleted). */
    public static final int NOT_PROMOTER = 660;
    /** Brokerage withdrawal below {@code litemall_brokerage_min_extract}. */
    public static final int EXTRACT_BELOW_MINIMUM = 661;
    /** Brokerage withdrawal exceeds the available {@code brokerage_price}. */
    public static final int EXTRACT_INSUFFICIENT = 662;
    /** Extract approve/reject on a row that is not PENDING (guarded 0-row update). */
    public static final int EXTRACT_INVALID_STATE = 663;
    /** Malformed affiliate request (bad paging, missing fields, bad amount). */
    public static final int BAD_REQUEST = 664;

    private AffiliateErrno() {
    }
}
