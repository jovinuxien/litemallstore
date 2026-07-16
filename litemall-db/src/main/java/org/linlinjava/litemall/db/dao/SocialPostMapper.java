package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallSocialPost;

import java.util.List;

/**
 * Hand-written mapper for the social publish ledger ({@code litemall_social_post},
 * V42). Mirrors {@link CjSourcingRequestMapper}: co-located with the generated
 * DAOs so the existing {@code @MapperScan} + {@code dao/*.xml} mapper-location
 * pattern picks it up.
 *
 * <p>Status transitions are guarded in SQL: only {@code draft|failed} rows can
 * move to {@code posted} or {@code failed} — a {@code posted} row is immutable
 * ledger history (the update methods return 0 rows instead of clobbering it).
 */
public interface SocialPostMapper {

    /** Insert a new ledger row; populates the generated id back onto the record. */
    int insert(LitemallSocialPost record);

    LitemallSocialPost selectById(@Param("id") Integer id);

    /** Page of (non-deleted) rows, newest first; null status/platform = no filter. */
    List<LitemallSocialPost> selectPage(@Param("status") String status,
                                        @Param("platform") String platform,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    int countPage(@Param("status") String status, @Param("platform") String platform);

    /** draft|failed → posted, recording the platform's post id and clearing the error. */
    int markPosted(@Param("id") Integer id, @Param("externalPostId") String externalPostId);

    /** draft|failed → failed with the (truncated-by-caller) error message. */
    int markFailed(@Param("id") Integer id, @Param("error") String error);

    /** Armed auto-post rows (posted_by='auto', auto_active=1) — the poller's dedupe set. */
    List<LitemallSocialPost> selectAutoArmed();

    /** Disarm one auto row (its deal's activation ended); keeps the ledger row itself. */
    int disarm(@Param("id") Integer id);
}
