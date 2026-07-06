package org.linlinjava.litemall.goods.interfaces.rest;

import jakarta.validation.constraints.NotNull;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.comment.CommentQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Anonymous customer product-review reads (litemall-wx-api {@code /wx/comment} parity), on
 * {@code /srv/comment}. Public per {@code litemall.svcsecurity.public-paths}. Posting a review is
 * authenticated and intentionally NOT here (order/user follow-up).
 *
 * <p>{@code valueId} is a String: a numeric goods id, or the {@code cj_&lt;pid&gt;} doc id the
 * index-only CJ detail page navigates with — CJ-sourced goods are served their CJ reviews
 * transparently (see {@link CommentQueryService}).
 */
@RestController
@RequestMapping("/srv/comment")
public class LitemallCommentController {

    private final CommentQueryService commentQueryService;

    public LitemallCommentController(CommentQueryService commentQueryService) {
        this.commentQueryService = commentQueryService;
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
}
