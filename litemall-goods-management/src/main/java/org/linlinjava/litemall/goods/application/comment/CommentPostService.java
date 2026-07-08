package org.linlinjava.litemall.goods.application.comment;

import org.linlinjava.litemall.db.domain.LitemallComment;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallCommentService;
import org.linlinjava.litemall.goods.application.engagement.EngagementGoodsResolver;
import org.linlinjava.litemall.goods.application.search.RankingSignalService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Authenticated product-review write path ({@code POST /srv/comment/post},
 * litemall-wx-api {@code /wx/comment/post} parity). The review lands in
 * {@code litemall_comment} against the resolved NUMERIC goods id, so
 * {@link CommentQueryService}'s local-reviews-take-precedence rule surfaces it
 * on the next {@code /srv/comment/list} read — including for CJ-sourced goods.
 *
 * <p><b>Purchase check — v1 decision (2026-07-07):</b> any authenticated user
 * may review; no purchased-this-goods verification. {@code litemall_comment}
 * carries no purchase flag, and order data lives in litemall-order — verifying
 * here would need a cross-context order facade, which stays out of
 * goods-management by directive. Raised as an {@code order}-worktree follow-up
 * (expose a "user purchased goods" query), not silently skipped.
 */
@Service
public class CommentPostService {

    /** SPA-enforced limit, mirrored server-side ({@code litemall_comment.content} is varchar(1023)). */
    private static final int MAX_CONTENT = 1023;

    private final LitemallCommentService commentService;
    private final EngagementGoodsResolver goodsResolver;
    private final RankingSignalService rankingSignalService;

    public CommentPostService(LitemallCommentService commentService,
                              EngagementGoodsResolver goodsResolver,
                              RankingSignalService rankingSignalService) {
        this.commentService = commentService;
        this.goodsResolver = goodsResolver;
        this.rankingSignalService = rankingSignalService;
    }

    /** The created comment id, or null when the request doesn't validate. */
    public Integer post(Integer userId, Byte type, String valueRef, Short star,
                        String content, String[] picUrls) {
        if (type == null || type != 0) {
            return null; // only goods reviews (type 0) are postable from the SPA
        }
        if (star == null || star < 1 || star > 5) {
            return null;
        }
        if (!StringUtils.hasText(content) || content.length() > MAX_CONTENT) {
            return null;
        }
        LitemallGoods goods = goodsResolver.resolve(valueRef);
        if (goods == null) {
            return null;
        }
        Integer valueId = goods.getId();

        LitemallComment comment = new LitemallComment();
        comment.setUserId(userId);
        comment.setType(type);
        comment.setValueId(valueId);
        comment.setStar(star);
        comment.setContent(content);
        String[] pics = picUrls != null ? picUrls : new String[0];
        comment.setHasPicture(pics.length > 0);
        comment.setPicUrls(pics);
        commentService.save(comment);
        // Refresh the goods' review aggregate (review_count/rating) so the new review feeds the
        // ranking boost on the next reindex. The doc itself refreshes via the goods write-path
        // GoodsIndexEvent; this keeps the persisted signal current for that re-index.
        rankingSignalService.refreshLocalReviewSignal(valueId);
        return comment.getId();
    }
}
