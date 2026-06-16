package org.linlinjava.litemall.goods.interfaces.rest;

import jakarta.validation.constraints.NotNull;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.comment.CommentQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous customer product-review reads (litemall-wx-api {@code /wx/comment} parity), on
 * {@code /srv/comment}. Public per {@code litemall.svcsecurity.public-paths}. Posting a review is
 * authenticated and intentionally NOT here (order/user follow-up).
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
                       @NotNull Integer valueId,
                       @NotNull Integer showType,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit) {
        CommentQueryService.CommentList result = commentQueryService.list(type, valueId, showType, page, limit);
        return ResponseUtil.okList(result.voList(), result.page());
    }

    @GetMapping("/count")
    public Object count(@NotNull Byte type, @NotNull Integer valueId) {
        return ResponseUtil.ok(commentQueryService.count(type, valueId));
    }
}
