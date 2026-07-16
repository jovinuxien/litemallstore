package org.linlinjava.litemall.promotion.domain.model.repositories;

import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallSocialPostAggregate;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPlatform;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPostStatus;

import java.util.List;
import java.util.Optional;

/**
 * Persistence boundary for the social publish ledger. Status transitions are
 * guarded in the underlying SQL (draft|failed → posted|failed; posted rows are
 * immutable), so the mark* methods report via their return value whether the
 * transition actually happened.
 */
public interface LitemallSocialPostRepository {

    /** Persist a new ledger row and return it with the generated id set. */
    LitemallSocialPostAggregate insert(LitemallSocialPostAggregate post);

    Optional<LitemallSocialPostAggregate> findById(Integer id);

    /** Newest-first page; null status/platform = unfiltered. */
    List<LitemallSocialPostAggregate> page(LitemallSocialPostStatus status,
                                           LitemallSocialPlatform platform,
                                           int page, int limit);

    int count(LitemallSocialPostStatus status, LitemallSocialPlatform platform);

    /** draft|failed → posted; false when the row was already posted (guard held). */
    boolean markPosted(Integer id, String externalPostId);

    /** draft|failed → failed with the error (truncated to the column width). */
    boolean markFailed(Integer id, String error);

    /** Armed auto rows (posted_by='auto', auto_active=1) — the auto-poster's dedupe set. */
    List<LitemallSocialPostAggregate> findAutoArmed();

    /** Disarm one auto row; its deal's activation has ended. */
    void disarm(Integer id);
}
