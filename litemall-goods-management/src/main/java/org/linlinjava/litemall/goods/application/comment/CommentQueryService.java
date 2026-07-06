package org.linlinjava.litemall.goods.application.comment;

import com.github.pagehelper.PageInfo;
import org.linlinjava.litemall.db.domain.LitemallComment;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.service.LitemallCommentService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productreview.CJProductComment;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productreview.CJProductReviewData;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anonymous customer read queries for product reviews (litemall-wx-api {@code WxCommentController}
 * parity — read paths only; posting a review is authenticated and left as an order/user follow-up).
 * Mirrors the wx vo: each comment carries its author's {@code userInfo} (nickName/avatarUrl).
 *
 * <p>CJ-aware: a goods target with no local reviews whose row is {@code source='cj'} (or a raw
 * {@code cj_&lt;pid&gt;} valueId from the index-only CJ detail page) is served from the CJ
 * "Product Comments" API through the cached/paced {@link CJProductService} — mapped to the SAME vo
 * shape, so the SPA cannot tell the sources apart. Local reviews always take precedence, and a CJ
 * outage degrades to an empty list, never an error. {@code showType=1} (with-picture) is not
 * supported by the CJ API and is ignored on the CJ path.
 */
@Service
public class CommentQueryService {

    private final LitemallCommentService commentService;
    private final LitemallUserService userService;
    private final LitemallGoodsService goodsService;
    private final CJProductService cjProductService;

    public CommentQueryService(LitemallCommentService commentService,
                               LitemallUserService userService,
                               LitemallGoodsService goodsService,
                               CJProductService cjProductService) {
        this.commentService = commentService;
        this.userService = userService;
        this.goodsService = goodsService;
        this.cjProductService = cjProductService;
    }

    /** One page of comment vos with real paging numbers, source-independent (local or CJ). */
    public record CommentPage(List<Map<String, Object>> list, long total, long pages, int page, int limit) {}

    /**
     * Reviews for {@code valueId} (type 0 = goods; numeric id or raw {@code cj_&lt;pid&gt;}),
     * {@code showType} 0 = all / 1 = with-picture (local path only). Each vo carries
     * addTime/content/adminContent/picList/star + the author's userInfo, matching wx.
     */
    public CommentPage list(Byte type, String valueId, Integer showType, Integer page, Integer limit) {
        String cjPid = cjPidFor(type, valueId);
        if (cjPid != null) {
            return cjList(cjPid, page, limit);
        }
        Integer id = parseId(valueId);
        if (id == null) {
            return new CommentPage(List.of(), 0, 0, page, limit);
        }
        List<LitemallComment> commentList = commentService.query(type, id, showType, page, limit);
        List<Map<String, Object>> voList = new ArrayList<>(commentList.size());
        for (LitemallComment comment : commentList) {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("addTime", comment.getAddTime());
            vo.put("content", comment.getContent());
            vo.put("adminContent", comment.getAdminContent());
            vo.put("picList", comment.getPicUrls());
            vo.put("star", comment.getStar());
            vo.put("userInfo", userInfo(comment.getUserId()));
            voList.add(vo);
        }
        PageInfo<LitemallComment> pageInfo = PageInfo.of(commentList);
        return new CommentPage(voList, pageInfo.getTotal(), pageInfo.getPages(), page, limit);
    }

    /** {allCount, hasPicCount} for a target, as wx returns (CJ targets report hasPicCount 0). */
    public Map<String, Object> count(Byte type, String valueId) {
        Map<String, Object> entity = new LinkedHashMap<>();
        String cjPid = cjPidFor(type, valueId);
        if (cjPid != null) {
            CJProductReviewData data = cjProductService.getProductComments(cjPid, 1, 1);
            entity.put("allCount", data != null ? parseLong(data.getTotal()) : 0L);
            entity.put("hasPicCount", 0L); // the CJ comments API has no with-picture filter
            return entity;
        }
        Integer id = parseId(valueId);
        entity.put("allCount", id != null ? commentService.count(type, id, 0) : 0L);
        entity.put("hasPicCount", id != null ? commentService.count(type, id, 1) : 0L);
        return entity;
    }

    /** True when the valueId is a plausible review target (numeric goods id or cj_&lt;pid&gt;). */
    public static boolean isValidValueId(String valueId) {
        return valueId != null && (valueId.startsWith("cj_") ? valueId.length() > 3 : parseId(valueId) != null);
    }

    /**
     * Resolve the CJ pid to serve reviews from, or null for the local path. Local reviews take
     * precedence: a numeric goods id with any local comment rows stays local even for a CJ row.
     */
    private String cjPidFor(Byte type, String valueId) {
        if (type == null || type != 0 || valueId == null) {
            return null; // only goods reviews (type 0) can come from CJ
        }
        if (valueId.startsWith("cj_")) {
            return valueId.substring(3); // index-only CJ detail page passes the raw doc id
        }
        Integer id = parseId(valueId);
        if (id == null || commentService.count(type, id, 0) > 0) {
            return null;
        }
        LitemallGoods goods = goodsService.findById(id);
        if (goods != null && "cj".equals(goods.getSource()) && StringUtils.hasText(goods.getCjPid())) {
            return goods.getCjPid();
        }
        return null;
    }

    /** One CJ review page mapped to the wx vo shape; CJ failure → empty page. */
    private CommentPage cjList(String pid, Integer page, Integer limit) {
        CJProductReviewData data = cjProductService.getProductComments(pid, page, limit);
        if (data == null || data.getList() == null) {
            return new CommentPage(List.of(), 0, 0, page, limit);
        }
        List<Map<String, Object>> voList = new ArrayList<>(data.getList().size());
        for (CJProductComment c : data.getList()) {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("addTime", c.getCommentDate()); // ISO string; local rows carry a LocalDateTime
            vo.put("content", c.getComment());
            vo.put("adminContent", null);
            vo.put("picList", c.getCommentUrls() != null ? c.getCommentUrls() : List.of());
            vo.put("star", parseStar(c.getScore()));
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("nickName", c.getCommentUser() != null ? c.getCommentUser() : "");
            info.put("avatarUrl", c.getFlagIconUrl() != null ? c.getFlagIconUrl() : "");
            vo.put("userInfo", info);
            voList.add(vo);
        }
        long total = parseLong(data.getTotal());
        long pages = limit != null && limit > 0 ? (total + limit - 1) / limit : 1;
        return new CommentPage(voList, total, pages, page, limit);
    }

    /** Minimal public author info (nickName/avatarUrl); anonymous/missing user → empty fields. */
    private Map<String, Object> userInfo(Integer userId) {
        Map<String, Object> info = new LinkedHashMap<>();
        LitemallUser user = userId == null ? null : userService.findById(userId);
        info.put("nickName", user != null ? user.getNickname() : "");
        info.put("avatarUrl", user != null ? user.getAvatar() : "");
        return info;
    }

    private static Integer parseId(String valueId) {
        try {
            return Integer.valueOf(valueId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** CJ scores arrive as strings; clamp to the wx 0-5 star range, defaulting to 5 on garbage. */
    private static short parseStar(String score) {
        try {
            return (short) Math.max(0, Math.min(5, Integer.parseInt(score.trim())));
        } catch (RuntimeException e) {
            return 5;
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (RuntimeException e) {
            return 0L;
        }
    }
}
