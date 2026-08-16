package org.linlinjava.litemall.db.dao;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;

/**
 * Focused, hand-written mapper for the CJ Dropshipping product snapshot
 * ({@code litemall_cj_product}). Unlike the MyBatis-Generator mappers in this
 * package it carries no {@code Example} machinery — only the access pattern the
 * CJ sync + indexing paths need.
 */
public interface LitemallCjProductMapper {

    /** All live (non-deleted) snapshot rows — the OCS indexing source. */
    List<LitemallCjProduct> selectAllLive();

    /** A page of live (non-deleted) snapshot rows, newest first — the paginated list-read source. */
    List<LitemallCjProduct> selectLivePaged(@Param("offset") int offset, @Param("limit") int limit);

    /** Count of live (non-deleted) snapshot rows — paging total. */
    int countLive();

    /** A single snapshot row by raw CJ pid (detail / order lookup), or null. */
    LitemallCjProduct selectByPid(@Param("pid") String pid);

    /** Raw pids of all live (non-deleted) rows — used to diff against the upstream set. */
    List<String> selectLivePids();

    /** Insert or refresh a snapshot row (keyed on the raw CJ pid). Clears the deleted flag on update. */
    int upsert(LitemallCjProduct row);

    /** Write ONLY the enriched fields (variants/attributes/discount/images) + stamp enriched_time. */
    int enrich(LitemallCjProduct row);

    /** Live rows due for detail+inventory enrichment: never-enriched first, then oldest. */
    List<LitemallCjProduct> selectForEnrichment(@Param("limit") int limit);

    /** Soft-delete the given raw pids (product removed upstream); returns rows affected. */
    int softDeleteByPids(@Param("pids") List<String> pids);

    /**
     * Supplier-attribution coverage over live enriched rows (V60 probe):
     * {@code {enrichedTotal, supplierPopulated}} counts in one query.
     */
    java.util.Map<String, Object> selectSupplierCoverage();

    /** Wave 26 Phase 1b: {enrichedTotal, probedTotal, euStocked} over enriched snapshot rows. */
    java.util.Map<String, Object> selectEuCoverage();

    /** Wave 26 Phase 1b: per-L1 EU survival over ON-SALE CJ goods, with an honest probed denominator. */
    java.util.List<java.util.Map<String, Object>> selectEuSurvivalByRoot();

    /** Wave 26: CJ explicitly reported no detail for this pid — count it. */
    int recordDelistedStrike(@Param("pid") String pid);

    /** Any successful detail fetch clears the count. */
    int clearDelistedStrikes(@Param("pid") String pid);

    /** Pids CJ has denied at least {@code minStrikes} times — the delisting candidates. */
    java.util.List<String> selectDelistedPids(@Param("minStrikes") int minStrikes);
}
