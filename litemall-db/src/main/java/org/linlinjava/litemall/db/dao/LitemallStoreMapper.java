package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallStore;

import java.util.List;

/**
 * Hand-written mapper for the pickup-store table ({@code litemall_store}, V35).
 * Mirrors {@link CjDisputeMapper}: co-located with the generated DAOs so the
 * existing {@code @MapperScan} + {@code dao/*.xml} mapper-location pattern picks it up.
 */
public interface LitemallStoreMapper {

    /** Customer-facing store picker: not deleted and visible (is_show=1). */
    List<LitemallStore> selectVisible();

    /** Admin list: all non-deleted stores, hidden ones included. */
    List<LitemallStore> selectAllAdmin();

    LitemallStore selectById(@Param("id") Integer id);

    /** Insert a new store; populates the generated id back onto the record. */
    int insert(LitemallStore record);

    /** Full update by id (all editable columns), bumps update_time. */
    int update(LitemallStore record);

    int logicalDelete(@Param("id") Integer id);
}
