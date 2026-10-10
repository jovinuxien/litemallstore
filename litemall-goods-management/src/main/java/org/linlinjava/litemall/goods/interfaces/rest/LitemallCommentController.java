package org.linlinjava.litemall.goods.interfaces.rest;

import jakarta.validation.constraints.NotNull;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.comment.CommentPostService;
import org.linlinjava.litemall.goods.application.comment.CommentQueryService;
import org.linlinjava.litemall.goods.domain.model.dto.goods.GoodsServiceResponseCode;
import org.linlinjava.litemall.goods.utils.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Customer product reviews (litemall-wx-api {@code /wx/comment} parity), on
 * {@code /srv/comment}. Reads are anonymous ({@code /srv/comment/**} is on the
 * svcsecurity public-paths list); the {@code /post} write therefore enforces
 * its own login check — the reviewer is the gateway-injected {@code X-User-Id},
 * and a missing identity gets {@code unlogin} despite the public path.
 *
 * <p>{@code valueId} is a String: a numeric goods id, or the {@code cj_&lt;pid&gt;} doc id the
 * index-only CJ detail page navigates with — CJ-sourced goods are served their CJ reviews
 * transparently (see {@link CommentQueryService}).
 *
 * <p>{@code /post} is purchase-gated (F17): errno {@code 670} = the caller never received this
 * product, {@code 671} = every purchase of it is already reviewed. The optional body field
 * {@code orderId} pins the check to one order; a non-numeric one is a bad argument.
 */
@RestController
@RequestMapping("/srv/comment")
public class LitemallCommentController {

    private final CommentQueryService commentQueryService;
    private final CommentPostService commentPostService;

    public LitemallCommentController(CommentQueryService commentQueryService,
                                     CommentPostService commentPostService) {
        this.commentQueryService = commentQueryService;
        this.commentPostService = commentPostService;
    }

    @GetMapping("/list")
    public Object list(@NotNull Byte type,
                       @NotNull String valueId,
                       @RequestParam(defaultValue = "0") Integer showType,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit) {
        if (!CommentQueryService.isValidValueId(valueId)) {
            return ResponseUtil.badArgumentValue();
        }
        CommentQueryService.CommentPage result = commentQueryService.list(type, valueId, showType, page, limit);
        // Same envelope okList(...) produces for a PageHelper page, source-independent.
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", result.total());
        data.put("pages", result.pages());
        data.put("limit", result.limit());
        data.put("page", result.page());
        data.put("list", result.list());
        return ResponseUtil.ok(data);
    }

    @GetMapping("/count")
    public Object count(@NotNull Byte type, @NotNull String valueId) {
        if (!CommentQueryService.isValidValueId(valueId)) {
            return ResponseUtil.badArgumentValue();
        }
        return ResponseUtil.ok(commentQueryService.count(type, valueId));
    }

    /** Body: {@code {type: 0, valueId, star, content, hasPicture?, picUrls?, orderId?}} (SPA ICommentPost). */
    @PostMapping("/post")
    public Object post(@RequestBody Map<String, Object> body) {
        Integer userId = UserContext.getUserIdAsInt();
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Integer orderId = null;
        if (body.get("orderId") != null && !String.valueOf(body.get("orderId")).isBlank()) {
            try {
                orderId = Integer.valueOf(String.valueOf(body.get("orderId")).trim());
            } catch (NumberFormatException e) {
                return ResponseUtil.badArgumentValue();
            }
        }
        Byte type = body.get("type") != null ? Byte.valueOf(String.valueOf(body.get("type"))) : 0;
        String valueId = body.get("valueId") != null ? String.valueOf(body.get("valueId")) : null;
        Short star;
        try {
            star = body.get("star") != null ? Short.valueOf(String.valueOf(body.get("star"))) : null;
        } catch (NumberFormatException e) {
            star = null;
        }
        String content = body.get("content") != null ? String.valueOf(body.get("content")) : null;
        String[] picUrls = null;
        if (body.get("picUrls") instanceof List<?> pics) {
            picUrls = pics.stream().map(String::valueOf).toArray(String[]::new);
        }
        CommentPostService.PostResult result;
        try {
            result = commentPostService.post(userId, type, valueId, star, content, picUrls, orderId);
        } catch (CommentPostService.ReviewSlotTakenException e) {
            result = new CommentPostService.PostResult(null, CommentPostService.Refusal.ALREADY_REVIEWED);
        }
        if (!result.ok()) {
            return switch (result.refusal()) {
                case NOT_PURCHASED -> ResponseUtil.fail(GoodsServiceResponseCode.REVIEW_NOT_PURCHASED,
                        "Only customers who received this product can review it");
                case ALREADY_REVIEWED -> ResponseUtil.fail(GoodsServiceResponseCode.REVIEW_ALREADY_POSTED,
                        "You have already reviewed this purchase");
                case INVALID -> ResponseUtil.badArgumentValue();
            };
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", result.id());
        return ResponseUtil.ok(data);
    }
}
