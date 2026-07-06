package org.linlinjava.litemall.goods.domain.service.elastic;

/**
 * CJ Dropshipping identity constants. <b>The {@code cj_<pid>} OCS indexing path it once owned was
 * retired in Phase 4</b> (OCS single-source): CJ products are now landed into the native
 * {@code litemall_goods} family by {@code CjProductPromotionService} and indexed through the same
 * native path as local goods ({@code LitemallProductIndexingService} / {@code SearchReindexService}),
 * so there is no longer a parallel {@code cj_}-prefixed document set.
 *
 * <p>What survives here are the two identity markers still referenced by the DB-served CJ detail page
 * ({@code CjGoodsDetailService}) and the snapshot normalizer ({@code CjSnapshotSyncService}):
 * <ul>
 *   <li>{@link #CJ_ID_PREFIX} — the {@code cj_} namespace prefix used to recognize/strip a CJ id, and</li>
 *   <li>{@link #SOURCE_CJ} — the {@code source} marker stamped on the {@code litemall_cj_product}
 *       snapshot rows and echoed in the detail response.</li>
 * </ul>
 * Pure constants holder — no bean, no CJ API calls, no document building.
 */
public final class CjProductIndexingService {

    /** The {@code cj_} namespace prefix marking a CJ id (snapshot detail lookup + order routing). */
    public static final String CJ_ID_PREFIX = "cj_";
    /** The {@code source} marker on {@code litemall_cj_product} snapshot rows / the detail response. */
    public static final String SOURCE_CJ = "cj_dropshipping";

    private CjProductIndexingService() {
    }
}
