package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallCjSourcingRequest;

import java.util.List;

/**
 * Hand-written mapper for the CJ sourcing-request projection table
 * ({@code litemall_cj_sourcing_request}, V32). Mirrors {@link CjDisputeMapper}:
 * co-located with the generated DAOs so the existing {@code @MapperScan} +
 * {@code dao/*.xml} mapper-location pattern picks it up.
 */
public interface CjSourcingRequestMapper {

    /** Insert a new sourcing-request row; populates the generated id back onto the record. */
    int insert(LitemallCjSourcingRequest record);

    LitemallCjSourcingRequest selectById(@Param("id") Integer id);

    LitemallCjSourcingRequest selectByCjSourcingId(@Param("cjSourcingId") String cjSourcingId);

    /** Page of (non-deleted) requests, newest first. */
    List<LitemallCjSourcingRequest> selectPage(@Param("offset") int offset, @Param("limit") int limit);

    int countAll();

    /** Rows that have a CJ sourcing id and can still change status at CJ (refresh candidates). */
    List<LitemallCjSourcingRequest> selectRefreshable(@Param("limit") int limit);

    /** Patch the CJ-synced projection fields (null args leave the column unchanged). */
    int updateCjProjection(LitemallCjSourcingRequest record);
}
