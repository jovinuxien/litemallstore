package org.linlinjava.litemall.goods.application.comment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.db.domain.LitemallComment;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallCommentService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productreview.CJProductComment;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productreview.CJProductReviewData;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class CjReviewIngestServiceTest {

    private CJDropshippingConfig config;
    private CJProductService cjProductService;
    private LitemallCommentService commentService;
    private LitemallGoodsService goodsService;
    private LitemallCjLinkageMapper linkageMapper;
    private CjReviewIngestService service;

    @BeforeEach
    void setup() {
        config = new CJDropshippingConfig();
        CJDropshippingConfig.Api api = new CJDropshippingConfig.Api();
        CJDropshippingConfig.Auth auth = new CJDropshippingConfig.Auth();
        auth.setCjApiKey("dev-key");
        api.setAuth(auth);
        config.setApi(api);

        cjProductService = Mockito.mock(CJProductService.class);
        commentService = Mockito.mock(LitemallCommentService.class);
        goodsService = Mockito.mock(LitemallGoodsService.class);
        linkageMapper = Mockito.mock(LitemallCjLinkageMapper.class);
        service = new CjReviewIngestService(config, cjProductService, commentService,
                goodsService, linkageMapper);
    }

    private static LitemallGoods cjGoods(Integer id, String pid) {
        LitemallGoods goods = new LitemallGoods();
        goods.setId(id);
        goods.setSource("cj");
        goods.setCjPid(pid);
        return goods;
    }

    private static CJProductComment cjComment(long id, String score, String date) {
        CJProductComment c = new CJProductComment();
        c.setCommentId(id);
        c.setComment("Great product " + id);
        c.setCommentUser("A***n");
        c.setScore(score);
        c.setCommentDate(date);
        c.setFlagIconUrl("https://flags.example/us.png");
        c.setCommentUrls(List.of("https://img.example/a.jpg"));
        return c;
    }

    private static CJProductReviewData page(String total, List<CJProductComment> list) {
        CJProductReviewData data = new CJProductReviewData();
        data.setTotal(total);
        data.setList(list);
        return data;
    }

    @Test
    void ingestsMapsAndMarks_shortPageStopsPaging() {
        LitemallGoods goods = cjGoods(7, "PID-1");
        when(goodsService.findById(7)).thenReturn(goods);
        when(cjProductService.getProductComments("PID-1", 1, CjReviewIngestService.PAGE_SIZE))
                .thenReturn(page("2", List.of(
                        cjComment(101, "5", "2025-11-02T10:15:30+08:00"),
                        cjComment(102, "9", "2025-11-01 08:00:00"))));

        service.ingestIfNeeded(goods);

        ArgumentCaptor<LitemallComment> captor = ArgumentCaptor.forClass(LitemallComment.class);
        verify(commentService, times(2)).saveRetainingTimes(captor.capture());
        LitemallComment first = captor.getAllValues().get(0);
        assertEquals(7, first.getValueId());
        assertEquals((byte) 0, first.getType());
        assertEquals(0, first.getUserId());
        assertEquals("cj", first.getSource());
        assertEquals("101", first.getExternalId());
        assertEquals("A***n", first.getAuthorName());
        assertEquals("https://flags.example/us.png", first.getAuthorAvatar());
        assertEquals((short) 5, first.getStar());
        assertEquals("Great product 101", first.getContent());
        assertTrue(first.getHasPicture());
        assertEquals(1, first.getPicUrls().length);
        assertEquals(LocalDateTime.of(2025, 11, 2, 10, 15, 30), first.getAddTime());
        // score "9" clamps to 5; space-separated date parses
        LitemallComment second = captor.getAllValues().get(1);
        assertEquals((short) 5, second.getStar());
        assertEquals(LocalDateTime.of(2025, 11, 1, 8, 0, 0), second.getAddTime());

        verify(linkageMapper).markCjReviewsIngested(7);
        // 2 < PAGE_SIZE → last page; no second CJ call
        verify(cjProductService, times(1)).getProductComments(anyString(), anyInt(), anyInt());
    }

    @Test
    void fetchesUpToMaxPagesOnFullPages() {
        LitemallGoods goods = cjGoods(8, "PID-2");
        when(goodsService.findById(8)).thenReturn(goods);
        List<CJProductComment> full = new ArrayList<>();
        for (int i = 0; i < CjReviewIngestService.PAGE_SIZE; i++) {
            full.add(cjComment(200 + i, "4", "2025-10-01"));
        }
        when(cjProductService.getProductComments(eq("PID-2"), anyInt(), eq(CjReviewIngestService.PAGE_SIZE)))
                .thenReturn(page("500", full));

        service.ingestIfNeeded(goods);

        verify(cjProductService, times(CjReviewIngestService.MAX_PAGES))
                .getProductComments(anyString(), anyInt(), anyInt());
        verify(commentService, times(CjReviewIngestService.MAX_PAGES * CjReviewIngestService.PAGE_SIZE))
                .saveRetainingTimes(any());
        verify(linkageMapper).markCjReviewsIngested(8);
    }

    @Test
    void skipsWithoutCjCall_whenNotEligible() {
        LitemallGoods local = new LitemallGoods();
        local.setId(1);
        local.setSource("local");
        service.ingestIfNeeded(local);

        LitemallGoods ingested = cjGoods(2, "PID-3");
        ingested.setCjReviewsIngestedTime(LocalDateTime.now());
        service.ingestIfNeeded(ingested);

        service.ingestIfNeeded(null);

        verifyZeroInteractions(cjProductService, linkageMapper, commentService);
    }

    @Test
    void skipsWithoutCjCall_whenAclDisabledOrKeyBlank() {
        config.setEnabled(false);
        service.ingestIfNeeded(cjGoods(3, "PID-4"));

        config.setEnabled(true);
        config.getApi().getAuth().setCjApiKey("  ");
        service.ingestIfNeeded(cjGoods(3, "PID-4"));

        verifyZeroInteractions(cjProductService, linkageMapper, commentService);
    }

    @Test
    void pageOneFailure_leavesMarkerNull_andCoolsDown() {
        LitemallGoods goods = cjGoods(9, "PID-5");
        when(goodsService.findById(9)).thenReturn(goods);
        when(cjProductService.getProductComments(anyString(), anyInt(), anyInt())).thenReturn(null);

        service.ingestIfNeeded(goods);
        service.ingestIfNeeded(goods); // within cooldown → no second CJ attempt

        verify(cjProductService, times(1)).getProductComments(anyString(), anyInt(), anyInt());
        verify(linkageMapper, never()).markCjReviewsIngested(anyInt());
    }

    @Test
    void duplicateRowsSkipped_stillMarks() {
        LitemallGoods goods = cjGoods(10, "PID-6");
        when(goodsService.findById(10)).thenReturn(goods);
        when(cjProductService.getProductComments("PID-6", 1, CjReviewIngestService.PAGE_SIZE))
                .thenReturn(page("1", List.of(cjComment(300, "5", "2025-09-09"))));
        when(commentService.saveRetainingTimes(any()))
                .thenThrow(new DuplicateKeyException("uk_comment_source_external"));

        service.ingestIfNeeded(goods);

        verify(linkageMapper).markCjReviewsIngested(10);
    }

    @Test
    void concurrentWinnerStampsMarker_secondCallSkipsViaFreshRead() {
        LitemallGoods stale = cjGoods(11, "PID-7");
        LitemallGoods fresh = cjGoods(11, "PID-7");
        fresh.setCjReviewsIngestedTime(LocalDateTime.now());
        when(goodsService.findById(11)).thenReturn(fresh);

        service.ingestIfNeeded(stale);

        verifyZeroInteractions(cjProductService);
        verify(linkageMapper, never()).markCjReviewsIngested(anyInt());
    }
}
