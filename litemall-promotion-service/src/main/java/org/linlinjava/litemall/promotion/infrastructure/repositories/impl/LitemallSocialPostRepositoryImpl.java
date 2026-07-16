package org.linlinjava.litemall.promotion.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.SocialPostMapper;
import org.linlinjava.litemall.db.domain.LitemallSocialPost;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallSocialPostAggregate;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallSocialPostRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPlatform;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPostStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class LitemallSocialPostRepositoryImpl implements LitemallSocialPostRepository {

    /** litemall_social_post.error is varchar(511); keep headroom for multi-byte chars. */
    private static final int ERROR_MAX_CHARS = 500;

    private final SocialPostMapper socialPostMapper;

    public LitemallSocialPostRepositoryImpl(SocialPostMapper socialPostMapper) {
        this.socialPostMapper = socialPostMapper;
    }

    @Override
    public LitemallSocialPostAggregate insert(LitemallSocialPostAggregate post) {
        LitemallSocialPost record = toData(post);
        LocalDateTime now = LocalDateTime.now();
        record.setAddTime(now);
        record.setUpdateTime(now);
        record.setDeleted(false);
        socialPostMapper.insert(record);
        post.setId(record.getId());
        post.setAddTime(now);
        post.setUpdateTime(now);
        return post;
    }

    @Override
    public Optional<LitemallSocialPostAggregate> findById(Integer id) {
        LitemallSocialPost entity = socialPostMapper.selectById(id);
        return entity != null ? Optional.of(toDomain(entity)) : Optional.empty();
    }

    @Override
    public List<LitemallSocialPostAggregate> page(LitemallSocialPostStatus status,
                                                  LitemallSocialPlatform platform,
                                                  int page, int limit) {
        int offset = Math.max(0, (page - 1) * limit);
        return socialPostMapper.selectPage(
                        status != null ? status.getDbValue() : null,
                        platform != null ? platform.getDbValue() : null,
                        offset, limit).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public int count(LitemallSocialPostStatus status, LitemallSocialPlatform platform) {
        return socialPostMapper.countPage(
                status != null ? status.getDbValue() : null,
                platform != null ? platform.getDbValue() : null);
    }

    @Override
    public boolean markPosted(Integer id, String externalPostId) {
        return socialPostMapper.markPosted(id, externalPostId) > 0;
    }

    @Override
    public boolean markFailed(Integer id, String error) {
        String truncated = error != null && error.length() > ERROR_MAX_CHARS
                ? error.substring(0, ERROR_MAX_CHARS)
                : error;
        return socialPostMapper.markFailed(id, truncated) > 0;
    }

    @Override
    public List<LitemallSocialPostAggregate> findAutoArmed() {
        return socialPostMapper.selectAutoArmed().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void disarm(Integer id) {
        socialPostMapper.disarm(id);
    }

    private LitemallSocialPostAggregate toDomain(LitemallSocialPost entity) {
        LitemallSocialPostAggregate post = new LitemallSocialPostAggregate();
        post.setId(entity.getId());
        post.setGoodsId(entity.getGoodsId());
        post.setPlatform(LitemallSocialPlatform.fromDbValue(entity.getPlatform()));
        post.setCaption(entity.getCaption());
        post.setMediaUrl(entity.getMediaUrl());
        post.setLinkUrl(entity.getLinkUrl());
        post.setStatus(LitemallSocialPostStatus.fromDbValue(entity.getStatus()));
        post.setExternalPostId(entity.getExternalPostId());
        post.setError(entity.getError());
        post.setPostedBy(entity.getPostedBy());
        post.setDealId(entity.getDealId());
        post.setAutoActive(Boolean.TRUE.equals(entity.getAutoActive()));
        post.setAddTime(entity.getAddTime());
        post.setUpdateTime(entity.getUpdateTime());
        return post;
    }

    private LitemallSocialPost toData(LitemallSocialPostAggregate post) {
        LitemallSocialPost record = new LitemallSocialPost();
        record.setId(post.getId());
        record.setGoodsId(post.getGoodsId());
        record.setPlatform(post.getPlatform() != null ? post.getPlatform().getDbValue() : null);
        record.setCaption(post.getCaption());
        record.setMediaUrl(post.getMediaUrl());
        record.setLinkUrl(post.getLinkUrl());
        record.setStatus(post.getStatus() != null ? post.getStatus().getDbValue() : null);
        record.setExternalPostId(post.getExternalPostId());
        record.setError(post.getError());
        record.setPostedBy(post.getPostedBy());
        record.setDealId(post.getDealId());
        record.setAutoActive(post.isAutoActive());
        return record;
    }
}
