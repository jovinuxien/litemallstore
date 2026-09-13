package org.linlinjava.litemall.goods.application.comment;

import org.linlinjava.litemall.db.domain.LitemallComment;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallOrderGoods;
import org.linlinjava.litemall.db.service.LitemallCommentService;
import org.linlinjava.litemall.goods.application.engagement.EngagementGoodsResolver;
import org.linlinjava.litemall.goods.application.search.RankingSignalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Authenticated product-review write path ({@code POST /srv/comment/post},
 * litemall-wx-api {@code /wx/comment/post} parity). The review lands in
 * {@code litemall_comment} against the resolved NUMERIC goods id (with the V44
 * {@code source} column defaulting to 'local'), so the next
 * {@code /srv/comment/list} read serves it merged newest-first alongside any
 * CJ-ingested rows — including for CJ-sourced goods.
 *
 * <p><b>Purchase check (F17, 2026-09-13; supersedes the 2026-07-07 v1 "any
 * authenticated user may review" decision):</b> a review is accepted only against
 * an unreviewed DELIVERED order line of that goods bought by the caller
 * ({@link PurchaseVerificationService}), and the consumed line is stamped with
 * the comment id in the SAME transaction. Two posts against one purchase cannot
 * both land: the stamp is a compare-and-set and a lost race rolls the review back
 * ({@link ReviewSlotTakenException}). Kill switch
 * {@code litemall.comment.require-purchase} (env
 * {@code LITEMALL_COMMENT_REQUIRE_PURCHASE}, default true): false restores the v1
 * behaviour exactly — no check, no stamp — by container recreate, no rebuild.
 */
@Service
public class CommentPostService {

    private static final Logger log = LoggerFactory.getLogger(CommentPostService.class);

    /** SPA-enforced limit, mirrored server-side ({@code litemall_comment.content} is varchar(1023)). */
    private static final int MAX_CONTENT = 1023;

    /** Why a post was refused; the controller maps each to its typed errno. */
    public enum Refusal {
        /** Malformed request (type, star, content, unknown goods) — badArgumentValue, as before. */
        INVALID,
        /** No delivered order line for this goods (or not for the passed order) — 670. */
        NOT_PURCHASED,
        /** Every delivered line of this goods already carries a review — 671. */
        ALREADY_REVIEWED
    }

    /** Exactly one of {@code id} / {@code refusal} is set. */
    public record PostResult(Integer id, Refusal refusal) {
        public boolean ok() {
            return id != null;
        }
        static PostResult created(Integer id) {
            return new PostResult(id, null);
        }
        static PostResult refused(Refusal refusal) {
            return new PostResult(null, refusal);
        }
    }

    /**
     * Thrown INSIDE the transaction when the compare-and-set stamp finds the line already
     * claimed, so the review insert rolls back; the controller answers ALREADY_REVIEWED.
     */
    public static class ReviewSlotTakenException extends RuntimeException {
        ReviewSlotTakenException(Integer orderGoodsId) {
            super("order line " + orderGoodsId + " was reviewed concurrently");
        }
    }

    private final LitemallCommentService commentService;
    private final EngagementGoodsResolver goodsResolver;
    private final RankingSignalService rankingSignalService;
    private final PurchaseVerificationService purchaseVerification;
    private final boolean requirePurchase;

    public CommentPostService(LitemallCommentService commentService,
                              EngagementGoodsResolver goodsResolver,
                              RankingSignalService rankingSignalService,
                              PurchaseVerificationService purchaseVerification,
                              @Value("${litemall.comment.require-purchase:true}") boolean requirePurchase) {
        this.commentService = commentService;
        this.goodsResolver = goodsResolver;
        this.rankingSignalService = rankingSignalService;
        this.purchaseVerification = purchaseVerification;
        this.requirePurchase = requirePurchase;
    }

    /** Whether the purchase check is on (exposed for the controller's javadoc-level honesty and tests). */
    public boolean isRequirePurchase() {
        return requirePurchase;
    }

    /**
     * @param orderId optional: restrict the purchase check to this order (the order-detail
     *                review form); null = the oldest unreviewed delivered line (the PDP form).
     * @throws ReviewSlotTakenException when the line was claimed by a concurrent post — the
     *         transaction is rolled back before the caller sees it.
     */
    @Transactional
    public PostResult post(Integer userId, Byte type, String valueRef, Short star,
                           String content, String[] picUrls, Integer orderId) {
        if (type == null || type != 0) {
            return PostResult.refused(Refusal.INVALID); // only goods reviews (type 0) are postable from the SPA
        }
        if (star == null || star < 1 || star > 5) {
            return PostResult.refused(Refusal.INVALID);
        }
        if (!StringUtils.hasText(content) || content.length() > MAX_CONTENT) {
            return PostResult.refused(Refusal.INVALID);
        }
        LitemallGoods goods = goodsResolver.resolve(valueRef);
        if (goods == null) {
            return PostResult.refused(Refusal.INVALID);
        }
        Integer valueId = goods.getId();

        LitemallOrderGoods line = null;
        if (requirePurchase) {
            PurchaseVerificationService.Eligibility eligibility =
                    purchaseVerification.findEligibleLine(userId, valueId, orderId);
            if (!eligibility.eligible()) {
                return PostResult.refused(switch (eligibility.refusal()) {
                    case NOT_PURCHASED -> Refusal.NOT_PURCHASED;
                    case ALREADY_REVIEWED -> Refusal.ALREADY_REVIEWED;
                });
            }
            line = eligibility.line();
        }

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

        if (line != null && !purchaseVerification.markReviewed(line.getId(), comment.getId())) {
            // Lost the compare-and-set: another post consumed this purchase between our read
            // and our stamp. Roll the review back rather than leave a second, unlinked one.
            log.info("Review for goods {} by user {} lost the order-line {} claim; rolling back",
                    valueId, userId, line.getId());
            throw new ReviewSlotTakenException(line.getId());
        }
        // Refresh the goods' review aggregate (review_count/rating) so the new review feeds the
        // ranking boost on the next reindex. The doc itself refreshes via the goods write-path
        // GoodsIndexEvent; this keeps the persisted signal current for that re-index.
        // CJ-sourced goods are exempt (V44): their aggregates are enrichment-owned CJ-side
        // totals, which a recompute from the capped ingested subset would clobber.
        if (!"cj".equals(goods.getSource())) {
            rankingSignalService.refreshLocalReviewSignal(valueId);
        }
        return PostResult.created(comment.getId());
    }
}
