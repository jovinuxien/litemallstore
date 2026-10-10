package org.linlinjava.litemall.goods.application.comment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallComment;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallOrderGoods;
import org.linlinjava.litemall.db.service.LitemallCommentService;
import org.linlinjava.litemall.goods.application.engagement.EngagementGoodsResolver;
import org.linlinjava.litemall.goods.application.search.RankingSignalService;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F17 write path: the review lands ONLY against an eligible purchase, the consumed line is
 * stamped with the new comment id, a lost stamp throws (so the transaction rolls back), and
 * the kill switch restores the v1 no-check/no-stamp behaviour exactly.
 */
public class CommentPostServiceTest {

    private LitemallCommentService commentService;
    private EngagementGoodsResolver goodsResolver;
    private RankingSignalService rankingSignalService;
    private PurchaseVerificationService purchaseVerification;

    @BeforeEach
    void setUp() {
        commentService = Mockito.mock(LitemallCommentService.class);
        goodsResolver = Mockito.mock(EngagementGoodsResolver.class);
        rankingSignalService = Mockito.mock(RankingSignalService.class);
        purchaseVerification = Mockito.mock(PurchaseVerificationService.class);
        LitemallGoods goods = new LitemallGoods();
        goods.setId(77);
        goods.setSource("local");
        when(goodsResolver.resolve("77")).thenReturn(goods);
        // save() assigns the generated id the way the mapper's useGeneratedKeys does.
        when(commentService.save(any())).thenAnswer(inv -> {
            ((LitemallComment) inv.getArgument(0)).setId(900);
            return 1;
        });
    }

    private CommentPostService service(boolean requirePurchase) {
        return new CommentPostService(commentService, goodsResolver, rankingSignalService,
                purchaseVerification, requirePurchase);
    }

    private static LitemallOrderGoods line(int id) {
        LitemallOrderGoods g = new LitemallOrderGoods();
        g.setId(id);
        g.setComment(0);
        return g;
    }

    @Test
    void eligiblePurchaseSavesReviewAndStampsTheLineWithTheCommentId() {
        when(purchaseVerification.findEligibleLine(5, 77, null))
                .thenReturn(PurchaseVerificationService.Eligibility.of(line(42)));
        when(purchaseVerification.markReviewed(42, 900)).thenReturn(true);

        CommentPostService.PostResult r = service(true).post(5, (byte) 0, "77", (short) 5, "Great", null, null);

        assertTrue(r.ok());
        assertEquals(900, r.id());
        ArgumentCaptor<LitemallComment> saved = ArgumentCaptor.forClass(LitemallComment.class);
        verify(commentService).save(saved.capture());
        assertEquals(5, saved.getValue().getUserId());
        assertEquals(77, saved.getValue().getValueId());
        verify(purchaseVerification).markReviewed(42, 900);
        verify(rankingSignalService).refreshLocalReviewSignal(77);
    }

    @Test
    void orderIdIsForwardedToTheEligibilityCheck() {
        when(purchaseVerification.findEligibleLine(5, 77, 913))
                .thenReturn(PurchaseVerificationService.Eligibility.of(line(42)));
        when(purchaseVerification.markReviewed(42, 900)).thenReturn(true);

        assertTrue(service(true).post(5, (byte) 0, "77", (short) 4, "ok", null, 913).ok());
        verify(purchaseVerification).findEligibleLine(5, 77, 913);
    }

    @Test
    void notPurchasedRefusesBeforeAnyWrite() {
        when(purchaseVerification.findEligibleLine(5, 77, null))
                .thenReturn(PurchaseVerificationService.Eligibility.refused(PurchaseVerificationService.Refusal.NOT_PURCHASED));

        CommentPostService.PostResult r = service(true).post(5, (byte) 0, "77", (short) 5, "Great", null, null);

        assertFalse(r.ok());
        assertEquals(CommentPostService.Refusal.NOT_PURCHASED, r.refusal());
        verify(commentService, never()).save(any());
        verify(purchaseVerification, never()).markReviewed(anyInt(), anyInt());
        verify(rankingSignalService, never()).refreshLocalReviewSignal(anyInt());
    }

    @Test
    void alreadyReviewedRefusesBeforeAnyWrite() {
        when(purchaseVerification.findEligibleLine(5, 77, null))
                .thenReturn(PurchaseVerificationService.Eligibility.refused(PurchaseVerificationService.Refusal.ALREADY_REVIEWED));

        CommentPostService.PostResult r = service(true).post(5, (byte) 0, "77", (short) 5, "Great", null, null);

        assertEquals(CommentPostService.Refusal.ALREADY_REVIEWED, r.refusal());
        verify(commentService, never()).save(any());
    }

    @Test
    void lostStampRaceThrowsSoTheTransactionRollsBackAndNoSignalRefreshHappens() {
        when(purchaseVerification.findEligibleLine(5, 77, null))
                .thenReturn(PurchaseVerificationService.Eligibility.of(line(42)));
        when(purchaseVerification.markReviewed(42, 900)).thenReturn(false);

        assertThrows(CommentPostService.ReviewSlotTakenException.class,
                () -> service(true).post(5, (byte) 0, "77", (short) 5, "Great", null, null));

        verify(commentService).save(any()); // the insert happened — and is what the rollback undoes
        verify(rankingSignalService, never()).refreshLocalReviewSignal(anyInt());
    }

    @Test
    void validationRefusalsStayInvalidAndNeverConsultPurchases() {
        CommentPostService s = service(true);
        assertEquals(CommentPostService.Refusal.INVALID, s.post(5, (byte) 1, "77", (short) 5, "x", null, null).refusal());
        assertEquals(CommentPostService.Refusal.INVALID, s.post(5, (byte) 0, "77", (short) 0, "x", null, null).refusal());
        assertEquals(CommentPostService.Refusal.INVALID, s.post(5, (byte) 0, "77", (short) 5, "  ", null, null).refusal());
        assertEquals(CommentPostService.Refusal.INVALID, s.post(5, (byte) 0, "77", (short) 5, "x".repeat(1024), null, null).refusal());
        assertEquals(CommentPostService.Refusal.INVALID, s.post(5, (byte) 0, "nope", (short) 5, "x", null, null).refusal());
        verify(purchaseVerification, never()).findEligibleLine(any(), any(), any());
        verify(commentService, never()).save(any());
    }

    @Test
    void killSwitchOffRestoresV1ExactlyNoCheckNoStamp() {
        CommentPostService.PostResult r = service(false).post(5, (byte) 0, "77", (short) 5, "Great", null, 913);

        assertTrue(r.ok());
        assertEquals(900, r.id());
        verify(purchaseVerification, never()).findEligibleLine(any(), any(), any());
        verify(purchaseVerification, never()).markReviewed(anyInt(), anyInt());
        verify(rankingSignalService).refreshLocalReviewSignal(77);
        assertFalse(service(false).isRequirePurchase());
    }

    @Test
    void cjGoodsSkipTheLocalSignalRefreshButStillStamp() {
        LitemallGoods cj = new LitemallGoods();
        cj.setId(88);
        cj.setSource("cj");
        when(goodsResolver.resolve("cj_abc")).thenReturn(cj);
        when(purchaseVerification.findEligibleLine(5, 88, null))
                .thenReturn(PurchaseVerificationService.Eligibility.of(line(43)));
        when(purchaseVerification.markReviewed(43, 900)).thenReturn(true);

        assertTrue(service(true).post(5, (byte) 0, "cj_abc", (short) 5, "Great", null, null).ok());
        verify(purchaseVerification).markReviewed(43, 900);
        verify(rankingSignalService, never()).refreshLocalReviewSignal(eq(88));
    }
}
