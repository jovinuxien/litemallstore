package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallPostizPost;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Hand-written mapper for the Postiz publish ledger ({@code litemall_postiz_post},
 * V50). Mirrors {@link SocialPostMapper}: co-located with the generated DAOs so
 * the existing {@code @MapperScan} + {@code dao/*.xml} mapper-location pattern
 * picks it up.
 */
public interface PostizPostMapper {

    /** Insert a new ledger row; populates the generated id back onto the record. */
    int insert(LitemallPostizPost record);

    /** Page of (non-deleted) rows, newest first. */
    List<LitemallPostizPost> selectPage(@Param("offset") int offset, @Param("limit") int limit);

    int countPage();

    /**
     * Successfully handed-off ({@code scheduled}) rows for any of the goods whose
     * schedule instant (UTC) falls after {@code sinceUtc} — the composer's
     * "posted N days ago" dedup-warning set. Includes future-scheduled rows.
     */
    List<LitemallPostizPost> selectScheduledSince(@Param("goodsIds") List<Integer> goodsIds,
                                                  @Param("sinceUtc") LocalDateTime sinceUtc);
}
