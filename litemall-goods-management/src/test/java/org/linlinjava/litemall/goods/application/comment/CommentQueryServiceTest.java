package org.linlinjava.litemall.goods.application.comment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallComment;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.service.LitemallCommentService;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.linlinjava.litemall.goods.application.engagement.EngagementGoodsResolver;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productreview.CJProductComment;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productreview.CJProductReviewData;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class CommentQueryServiceTest {

    private LitemallCommentService commentService;
    private LitemallUserService userService;
    private CJProductService cjProductService;
    private EngagementGoodsResolver goodsResolver;
    private CjReviewIngestService reviewIngestService;
    private CommentQueryService service;

    @BeforeEach
    void setup() {
        commentService = Mockito.mock(LitemallCommentService.class);
        userService = Mockito.mock(LitemallUserService.class);
        cjProductService = Mockito.mock(CJProductService.class);
        goodsResolver = Mockito.mock(EngagementGoodsResolver.class);
        reviewIngestService = Mockito.mock(CjReviewIngestService.class);
        service = new CommentQueryService(commentService, userService, cjProductService,
                goodsResolver, reviewIngestService);
    }

    private static LitemallGoods cjGoods(Integer id) {
        LitemallGoods goods = new LitemallGoods();
        goods.setId(id);
        goods.setSource("cj");
        goods.setCjPid("PID-" + id);
        return goods;
    }

    private static LitemallComment cjRow() {
        LitemallComment c = new LitemallComment();
        c.setSource("cj");
        c.setAuthorName("M***a");
        c.setAuthorAvatar("https://flags.example/mx.png");
        c.setContent("imported");
        c.setStar((short) 5);
        c.setAddTime(LocalDateTime.of(2025, 11, 2, 0, 0));
        return c;
    }

    private static LitemallComment localRow(int userId) {
        LitemallComment c = new LitemallComment();
        c.setSource("local");
        c.setUserId(userId);
        c.setContent("posted");
        c.setStar((short) 4);
        c.setAddTime(LocalDateTime.of(2026, 7, 20, 0, 0));
        return c;
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_servesMergedRows_authorPerSource() {
        LitemallGoods goods = cjGoods(42);
        when(goodsResolver.resolve("42")).thenReturn(goods);
        when(commentService.query((byte) 0, 42, 0, 1, 10))
                .thenReturn(List.of(localRow(5), cjRow()));
        LitemallUser user = new LitemallUser();
        user.setNickname("bimeni");
        user.setAvatar("https://a/u.png");
        when(userService.findById(5)).thenReturn(user);

        CommentQueryService.CommentPage result = service.list((byte) 0, "42", 0, 1, 10);

        verify(reviewIngestService).ingestIfNeeded(goods);
        assertEquals(2, result.list().size());
        Map<String, Object> localInfo = (Map<String, Object>) result.list().get(0).get("userInfo");
        assertEquals("bimeni", localInfo.get("nickName"));
        Map<String, Object> cjInfo = (Map<String, Object>) result.list().get(1).get("userInfo");
        assertEquals("M***a", cjInfo.get("nickName"));
        assertEquals("https://flags.example/mx.png", cjInfo.get("avatarUrl"));
        verifyZeroInteractions(cjProductService); // resolvable target never hits CJ from the read path
    }

    @Test
    void list_cjRefResolved_servedLocally() {
        LitemallGoods goods = cjGoods(77);
        when(goodsResolver.resolve("cj_PID-77")).thenReturn(goods);
        when(commentService.query((byte) 0, 77, 0, 1, 10)).thenReturn(List.of());

        service.list((byte) 0, "cj_PID-77", 0, 1, 10);

        verify(reviewIngestService).ingestIfNeeded(goods);
        verify(commentService).query((byte) 0, 77, 0, 1, 10);
        verifyZeroInteractions(cjProductService);
    }

    @Test
    void list_unresolvableCjRef_fallsBackToPassThrough() {
        when(goodsResolver.resolve("cj_GHOST")).thenReturn(null);
        CJProductComment c = new CJProductComment();
        c.setComment("index-only");
        c.setScore("4");
        CJProductReviewData data = new CJProductReviewData();
        data.setTotal("1");
        data.setList(List.of(c));
        when(cjProductService.getProductComments("GHOST", 1, 10)).thenReturn(data);

        CommentQueryService.CommentPage result = service.list((byte) 0, "cj_GHOST", 0, 1, 10);

        assertEquals(1, result.total());
        assertEquals("index-only", result.list().get(0).get("content"));
    }

    @Test
    void list_numericWithoutLiveGoods_stillServesLocalRows() {
        when(goodsResolver.resolve("99")).thenReturn(null);
        when(commentService.query((byte) 0, 99, 0, 1, 10)).thenReturn(List.of(localRow(1)));

        CommentQueryService.CommentPage result = service.list((byte) 0, "99", 0, 1, 10);

        assertEquals(1, result.list().size());
        verifyZeroInteractions(cjProductService, reviewIngestService);
    }

    @Test
    void count_servesLocalCounts_afterIngestTrigger() {
        LitemallGoods goods = cjGoods(42);
        when(goodsResolver.resolve("42")).thenReturn(goods);
        when(commentService.count((byte) 0, 42, 0)).thenReturn(61);
        when(commentService.count((byte) 0, 42, 1)).thenReturn(12);

        Map<String, Object> counts = service.count((byte) 0, "42");

        verify(reviewIngestService).ingestIfNeeded(goods);
        assertEquals(61L, counts.get("allCount"));
        assertEquals(12L, counts.get("hasPicCount"));
        verifyZeroInteractions(cjProductService);
    }
}
