package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.db.domain.OrderExportVo;
import org.linlinjava.litemall.db.domain.OrderVo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface OrderMapper {
    int updateWithOptimisticLocker(@Param("lastUpdateTime") LocalDateTime lastUpdateTime, @Param("order") LitemallOrder order);
    List<Map> getOrderIds(@Param("query") String query, @Param("orderByClause") String orderByClause);
    List<OrderVo> getOrderList(@Param("query") String query, @Param("orderByClause") String orderByClause);

    /**
     * Hand-maintained (V33): ids of CJ-fulfilled orders the lifecycle sync should poll —
     * placed at CJ (cj_order_id set), CJ status not yet terminal, local status not terminal.
     * Least-recently-updated first so a stalled order never starves behind fresh ones.
     */
    List<Integer> selectSyncableCjOrderIds(@Param("limit") int limit);

    /**
     * Hand-maintained (Wave 8): ids of PAID CJ orders not yet placed at CJ — the durable
     * placement queue (the order row IS the job; no outbox table). Excludes orders marked
     * with the local sentinel {@code cj_order_status='PLACEMENT_REJECTED'} (terminal CJ
     * business rejection awaiting ops; clear the sentinel to requeue). Least-recently-updated
     * first, LIMIT-capped like the sync sweep.
     */
    List<Integer> selectPlaceableCjOrderIds(@Param("limit") int limit);

    /**
     * Hand-maintained (V59, Wave 23): {@link #selectPlaceableCjOrderIds} restricted to
     * admin-APPROVED orders ({@code cj_placement_approved_time is not null}) — the sweep
     * predicate in placement-mode 'manual'. Unapproved orders stay durably queued (the
     * paid-and-unplaced predicate never expires) but never reach CJ.
     */
    List<Integer> selectApprovedPlaceableCjOrderIds(@Param("limit") int limit);

    /**
     * Hand-maintained (V59, Wave 23): paged pending list for the admin CJ-approval panel —
     * paid CJ orders not yet placed at CJ and not yet approved, oldest paid first.
     * (Rows carrying the PLACEMENT_REJECTED sentinel are included: they are paid,
     * unplaced and need admin attention; the controller surfaces the hold reason.)
     */
    List<LitemallOrder> selectCjPlacementPending(@Param("offset") int offset, @Param("limit") int limit);

    /** Hand-maintained (V59, Wave 23): total for {@link #selectCjPlacementPending}. */
    long countCjPlacementPending();

    /**
     * Hand-maintained (V59, Wave 23): stamp admin approval for CJ placement — CAS on
     * {@code cj_placement_approved_time is null} so the stamp is written exactly once
     * (concurrent/repeat approvals return 0 and the first stamp wins). Guarded to
     * paid ({@code order_status=201}), unplaced ({@code cj_order_id is null}) CJ orders;
     * the caller distinguishes "already approved" from "not eligible" by re-reading.
     */
    int stampCjPlacementApproval(@Param("id") Integer id, @Param("approvedBy") String approvedBy);

    /**
     * Hand-maintained (V35): stamp a pickup write-off code at pay time, but only once —
     * the {@code verify_code is null} guard makes generation idempotent under replays.
     * Returns 0 if the order already carries a code (caller keeps the existing one).
     */
    int setVerifyCodeIfAbsent(@Param("id") Integer id, @Param("verifyCode") String verifyCode);

    /**
     * Hand-maintained (V35): write a pickup order off — 201 (paid) straight to 401
     * (confirmed/received), stamping verify_time and who performed it. Guarded so a code
     * can only be redeemed once and only on a paid order; returns 0 when not redeemable.
     */
    int markDeliveredByWriteoff(@Param("id") Integer id, @Param("verifiedBy") String verifiedBy);

    /**
     * Hand-maintained (Wave 4): keyset-paged lean projection for admin order export
     * (no joins). Pass lastId=0 for the first page; every filter is optional.
     */
    List<OrderExportVo> selectExportRows(@Param("userId") Integer userId,
                                         @Param("orderSn") String orderSn,
                                         @Param("orderStatuses") List<Short> orderStatuses,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end,
                                         @Param("lastId") Integer lastId,
                                         @Param("limit") int limit);

    /**
     * Hand-maintained (Wave 4): order count + revenue grouped by fulfillment source
     * ('local' | 'cj'; blank/NULL source counted as 'local'). Keys: source, orders, amount.
     */
    List<Map<String, Object>> statBySource(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);

    /**
     * Hand-maintained (Wave 4): order count + revenue grouped by tender, derived from the
     * pay_id prefix (e.g. 'WALLET:...' / 'CARD:...'); unpaid orders bucket as 'UNPAID'.
     * Keys: tender, orders, amount.
     */
    List<Map<String, Object>> statByTender(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);
}
