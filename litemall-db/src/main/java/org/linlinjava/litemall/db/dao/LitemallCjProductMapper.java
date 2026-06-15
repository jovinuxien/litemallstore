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

    /** A single snapshot row by raw CJ pid (detail / order lookup), or null. */
    LitemallCjProduct selectByPid(@Param("pid") String pid);

    /** Raw pids of all live (non-deleted) rows — used to diff against the upstream set. */
    List<String> selectLivePids();

    /** Insert or refresh a snapshot row (keyed on the raw CJ pid). Clears the deleted flag on update. */
    int upsert(LitemallCjProduct row);

    /** Soft-delete the given raw pids (product removed upstream); returns rows affected. */
    int softDeleteByPids(@Param("pids") List<String> pids);
}
