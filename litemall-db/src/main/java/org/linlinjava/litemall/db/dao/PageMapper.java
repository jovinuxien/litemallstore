package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallPage;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Hand-written mapper for {@code litemall_page} (V36, DIY pages / palette v1).
 * Mirrors {@link CjSourcingRequestMapper}: co-located with the generated DAOs
 * so the existing {@code @MapperScan} + {@code dao/*.xml} mapper-location
 * pattern picks it up.
 *
 * <p>Status transitions go through the dedicated statements below (never
 * {@link #updateSelective}). The single-active-home invariant is enforced by
 * the DB's generated-column UNIQUE index: {@link #activate} on a second home
 * page without a prior {@link #demoteActiveHome} in the same transaction
 * throws a duplicate-key exception — callers treat that as a lost race.
 */
public interface PageMapper {

    /** Insert; populates the generated id back onto the record. */
    int insert(LitemallPage record);

    /** Non-deleted, any status. Loads config. */
    LitemallPage selectById(@Param("id") Integer id);

    /** The single active home page, or null. Loads config. */
    LitemallPage selectActiveHome();

    /** Active + non-deleted only (customer /srv/page/{id}). Loads config. */
    LitemallPage selectActiveById(@Param("id") Integer id);

    /**
     * Admin list, newest first, WITHOUT the (potentially 64KB) config column.
     * Nullable filters: position, status (exact).
     */
    List<LitemallPage> selectAdminPage(@Param("position") String position,
                                       @Param("status") String status,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    int countAdmin(@Param("position") String position, @Param("status") String status);

    /** Selective update of name/config only; update_time always written. */
    int updateSelective(LitemallPage record);

    /** Demote the current active home to draft (0 rows = none was active). */
    int demoteActiveHome(@Param("updateTime") LocalDateTime updateTime);

    /** Set status=active. Duplicate-key here = lost the activation race. */
    int activate(@Param("id") Integer id, @Param("updateTime") LocalDateTime updateTime);

    /** Set status=draft. */
    int deactivate(@Param("id") Integer id, @Param("updateTime") LocalDateTime updateTime);

    int logicalDeleteById(@Param("id") Integer id, @Param("updateTime") LocalDateTime updateTime);
}
