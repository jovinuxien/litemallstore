package org.linlinjava.litemall.promotion.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.PostizPostMapper;
import org.linlinjava.litemall.db.domain.LitemallPostizPost;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallPostizPostRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link LitemallPostizPostRepository} over the hand-written V50 mapper.
 * Timestamps are set here (add/update) so callers only describe the attempt.
 */
@Repository
public class LitemallPostizPostRepositoryImpl implements LitemallPostizPostRepository {

    private final PostizPostMapper mapper;

    public LitemallPostizPostRepositoryImpl(PostizPostMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(LitemallPostizPost row) {
        LocalDateTime now = LocalDateTime.now();
        row.setAddTime(now);
        row.setUpdateTime(now);
        if (row.getDeleted() == null) {
            row.setDeleted(false);
        }
        mapper.insert(row);
    }

    @Override
    public PostizPostPage page(int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int total = mapper.countPage();
        List<LitemallPostizPost> rows = mapper.selectPage((safePage - 1) * safeLimit, safeLimit);
        return new PostizPostPage(total, rows);
    }

    @Override
    public List<LitemallPostizPost> scheduledSince(List<Integer> goodsIds, LocalDateTime sinceUtc) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectScheduledSince(goodsIds, sinceUtc);
    }
}
