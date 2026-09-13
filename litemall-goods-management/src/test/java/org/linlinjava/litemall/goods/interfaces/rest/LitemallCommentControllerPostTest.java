package org.linlinjava.litemall.goods.interfaces.rest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.goods.application.comment.CommentPostService;
import org.linlinjava.litemall.goods.application.comment.CommentQueryService;
import org.linlinjava.litemall.goods.utils.UserContext;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F17 envelope contract for {@code POST /srv/comment/post}: 670 not purchased, 671 already
 * reviewed (including the concurrent-claim rollback), bad-argument for junk, unlogin without an
 * identity, {@code {id}} on success; {@code orderId} is optional and forwarded when numeric.
 */
public class LitemallCommentControllerPostTest {

    private CommentPostService postService;
    private LitemallCommentController controller;

    @BeforeEach
    void setUp() {
        postService = Mockito.mock(CommentPostService.class);
        controller = new LitemallCommentController(Mockito.mock(CommentQueryService.class), postService);
        UserContext.setUserId("5");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }

    private static Map<String, Object> body(Object orderId) {
        Map<String, Object> b = new HashMap<>();
        b.put("type", 0);
        b.put("valueId", "77");
        b.put("star", 5);
        b.put("content", "Great");
        b.put("picUrls", List.of("/_cdn/a.jpg"));
        if (orderId != null) {
            b.put("orderId", orderId);
        }
        return b;
    }

    @Test
    void successAnswersTheCommentId() {
        when(postService.post(eq(5), eq((byte) 0), eq("77"), eq((short) 5), eq("Great"), any(), isNull()))
                .thenReturn(new CommentPostService.PostResult(900, null));

        Map<String, Object> env = asMap(controller.post(body(null)));

        assertEquals(0, env.get("errno"));
        assertEquals(900, asMap(env.get("data")).get("id"));
    }

    @Test
    void numericOrderIdIsForwardedAndJunkIsABadArgument() {
        when(postService.post(any(), any(), any(), any(), any(), any(), eq(913)))
                .thenReturn(new CommentPostService.PostResult(900, null));

        assertEquals(0, asMap(controller.post(body(913))).get("errno"));
        assertEquals(0, asMap(controller.post(body("913"))).get("errno"));
        verify(postService, Mockito.times(2)).post(any(), any(), any(), any(), any(), any(), eq(913));

        assertEquals(402, asMap(controller.post(body("abc"))).get("errno"));
        verify(postService, never()).post(any(), any(), any(), any(), any(), any(), isNull());
    }

    @Test
    void notPurchasedIs670WithTheCustomerFacingReason() {
        when(postService.post(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new CommentPostService.PostResult(null, CommentPostService.Refusal.NOT_PURCHASED));

        Map<String, Object> env = asMap(controller.post(body(null)));

        assertEquals(670, env.get("errno"));
        assertEquals("Only customers who received this product can review it", env.get("errmsg"));
    }

    @Test
    void alreadyReviewedIs671AndSoIsALostConcurrentClaim() {
        when(postService.post(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new CommentPostService.PostResult(null, CommentPostService.Refusal.ALREADY_REVIEWED));
        assertEquals(671, asMap(controller.post(body(null))).get("errno"));

        when(postService.post(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(Mockito.mock(CommentPostService.ReviewSlotTakenException.class));
        Map<String, Object> env = asMap(controller.post(body(null)));
        assertEquals(671, env.get("errno"));
        assertEquals("You have already reviewed this purchase", env.get("errmsg"));
    }

    @Test
    void invalidStaysBadArgumentAndMissingIdentityStaysUnlogin() {
        when(postService.post(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new CommentPostService.PostResult(null, CommentPostService.Refusal.INVALID));
        assertEquals(402, asMap(controller.post(body(null))).get("errno"));

        UserContext.clear();
        assertEquals(501, asMap(controller.post(body(null))).get("errno"));
        verify(postService, Mockito.times(1)).post(any(), any(), any(), any(), any(), any(), any());
    }
}
