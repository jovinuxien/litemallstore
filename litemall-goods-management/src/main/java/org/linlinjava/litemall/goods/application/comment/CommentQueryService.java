package org.linlinjava.litemall.goods.application.comment;

import com.github.pagehelper.PageInfo;
import org.linlinjava.litemall.db.domain.LitemallComment;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.service.LitemallCommentService;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.linlinjava.litemall.goods.application.engagement.EngagementGoodsResolver;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productreview.CJProductComment;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productreview.CJProductReviewData;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anonymous customer read queries for product reviews (litemall-wx-api {@code WxCommentController}
 * parity — read paths only; posting is {@link CommentPostService}). Mirrors the wx vo: each
 * comment carries its author's {@code userInfo} (nickName/avatarUrl).
 *
 * <p><b>CJ-aware since V44 (Wave 8):</b> the first read of a CJ-sourced good triggers the
 * demand-driven {@link CjReviewIngestService} — its newest CJ reviews land ONCE in
 * {@code litemall_comment} ({@code source='cj'}) and every read, including this first one, is
 * served from the local store. Customer-posted and CJ-ingested reviews therefore merge into a
 * single list, ordered <b>newest-first by add_time</b> (CJ rows keep their original CJ comment
 * date) — chosen over rating-weighting so fresh customer feedback is never buried under years
 * of imported five-star rows. A disabled/unreachable CJ ACL degrades to the local rows (empty
 * on prod today), never an error, and leaves the good ingestable later.
 *
 * <p>The {@code cj_&lt;pid&gt;} valueId resolves to the promoted native row
 * ({@link EngagementGoodsResolver}); only a truly index-only doc (no native row, edge case)
 * falls back to the legacy uncached CJ pass-through.
 */
@Service
public class CommentQueryService {

    private final LitemallCommentService commentService;
    private final LitemallUserService userService;
    private final CJProductService cjProductService;
    private final EngagementGoodsResolver goodsResolver;
    private final CjReviewIngestService reviewIngestService;

    public CommentQueryService(LitemallCommentService commentService,
                               LitemallUserService userService,
                               CJProductService cjProductService,
                               EngagementGoodsResolver goodsResolver,
                               CjReviewIngestService reviewIngestService) {
        this.commentService = commentService;
        this.userService = userService;
        this.cjProductService = cjProductService;
        this.goodsResolver = goodsResolver;
        this.reviewIngestService = reviewIngestService;
    }

    /** One page of comment vos with real paging numbers, source-independent (local or CJ). */
    public record CommentPage(List<Map<String, Object>> list, long total, long pages, int page, int limit) {}

    /**
     * Reviews for {@code valueId} (type 0 = goods; numeric id or raw {@code cj_&lt;pid&gt;}),
     * {@code showType} 0 = all / 1 = with-picture. Each vo carries
     * addTime/content/adminContent/picList/star + the author's userInfo, matching wx.
     */
    public CommentPage list(Byte type, String valueId, Integer showType, Integer page, Integer limit) {
        Integer id = resolveTarget(type, valueId);
        if (id == null) {
            if (isCjRef(type, valueId)) {
                return cjPassThroughList(valueId.substring(3), page, limit);
            }
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
            vo.put("userInfo", userInfo(comment));
            voList.add(vo);
        }
        PageInfo<LitemallComment> pageInfo = PageInfo.of(commentList);
        return new CommentPage(voList, pageInfo.getTotal(), pageInfo.getPages(), page, limit);
    }

    /** {allCount, hasPicCount} for a target, as wx returns. */
    public Map<String, Object> count(Byte type, String valueId) {
        Map<String, Object> entity = new LinkedHashMap<>();
        Integer id = resolveTarget(type, valueId);
        if (id == null && isCjRef(type, valueId)) {
            CJProductReviewData data = cjProductService.getProductComments(valueId.substring(3), 1, 1);
            entity.put("allCount", data != null ? parseLong(data.getTotal()) : 0L);
            entity.put("hasPicCount", 0L); // the CJ comments API has no with-picture filter
            return entity;
        }
        entity.put("allCount", id != null ? (long) commentService.count(type, id, 0) : 0L);
        entity.put("hasPicCount", id != null ? (long) commentService.count(type, id, 1) : 0L);
        return entity;
    }

    /** True when the valueId is a plausible review target (numeric goods id or cj_&lt;pid&gt;). */
    public static boolean isValidValueId(String valueId) {
        return valueId != null && (valueId.startsWith("cj_") ? valueId.length() > 3 : parseId(valueId) != null);
    }

    /**
     * Resolve the LOCAL id to serve from, triggering the one-time CJ ingest for CJ-sourced
     * goods on the way. Null only for an unresolvable reference (bad id, or an index-only
     * {@code cj_&lt;pid&gt;} doc with no promoted native row — the caller's pass-through case).
     */
    private Integer resolveTarget(Byte type, String valueId) {
        if (type != null && type == 0) {
            LitemallGoods goods = goodsResolver.resolve(valueId);
            if (goods != null) {
                reviewIngestService.ingestIfNeeded(goods); // no-op unless a never-ingested CJ good
                return goods.getId();
            }
            // No live goods row: a numeric id still serves its local rows (pre-V44 behavior,
            // e.g. reviews of a since-deleted good); only an unresolved cj_ ref returns null.
            return valueId != null && valueId.startsWith("cj_") ? null : parseId(valueId);
        }
        return parseId(valueId); // non-goods comment types stay purely local
    }

    private static boolean isCjRef(Byte type, String valueId) {
        return type != null && type == 0 && valueId != null && valueId.startsWith("cj_") && valueId.length() > 3;
    }

    /**
     * Legacy uncached pass-through for an index-only CJ doc (no native goods row to attach
     * rows to — litemall_comment.value_id is INT). One CJ review page mapped to the wx vo
     * shape; CJ failure or disabled ACL → empty page, never an error.
     */
    private CommentPage cjPassThroughList(String pid, Integer page, Integer limit) {
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

    /**
     * Minimal public author info (nickName/avatarUrl). CJ-ingested rows carry their (masked)
     * author inline; local rows resolve the posting user. Anonymous/missing → empty fields.
     */
    private Map<String, Object> userInfo(LitemallComment comment) {
        Map<String, Object> info = new LinkedHashMap<>();
        if ("cj".equals(comment.getSource())) {
            info.put("nickName", comment.getAuthorName() != null ? comment.getAuthorName() : "");
            info.put("avatarUrl", comment.getAuthorAvatar() != null ? comment.getAuthorAvatar() : "");
            return info;
        }
        LitemallUser user = comment.getUserId() == null ? null : userService.findById(comment.getUserId());
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
