package org.linlinjava.litemall.goods.interfaces.rest.admin;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.db.domain.LitemallComment;
import org.linlinjava.litemall.db.service.LitemallCommentService;
import org.linlinjava.litemall.goods.domain.model.dto.goods.GoodsServiceResponseCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin product-comment moderation, ported from litemall-admin-api
 * ({@code admin.web.AdminCommentController}). Mounted under {@code /srv/private/admin/**}
 * (ROLE_ADMIN-gated by litemall-svcsecurity).
 */
@RestController
@RequestMapping("/srv/private/admin/comment")
@Validated
public class AdminCommentController {
    private final Log logger = LogFactory.getLog(AdminCommentController.class);

    @Autowired
    private LitemallCommentService commentService;

    @GetMapping("/list")
    public Object list(String userId, String valueId,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit,
                       @Sort @RequestParam(defaultValue = "add_time") String sort,
                       @Order @RequestParam(defaultValue = "desc") String order) {
        List<LitemallComment> commentList = commentService.querySelective(userId, valueId, page, limit, sort, order);
        return ResponseUtil.okList(commentList);
    }

    @PostMapping("/delete")
    public Object delete(@RequestBody LitemallComment comment) {
        Integer id = comment.getId();
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        commentService.deleteById(id);
        return ResponseUtil.ok();
    }

    /**
     * One-shot admin reply to a customer comment (litemall-admin-api {@code /admin/comment/reply}
     * parity): a comment can be replied to exactly once — a second attempt returns errno
     * {@link GoodsServiceResponseCode#ORDER_REPLY_EXIST} (622).
     */
    @PostMapping("/reply")
    public Object reply(@RequestBody ReplyRequest body) {
        if (body == null || body.getCommentId() == null
                || body.getContent() == null || body.getContent().isBlank()) {
            return ResponseUtil.badArgument();
        }
        // admin_content is varchar(511).
        if (body.getContent().length() > 511) {
            return ResponseUtil.badArgumentValue();
        }
        LitemallComment comment = commentService.findById(body.getCommentId());
        // findById is selectByPrimaryKey — it does NOT filter deleted, so check it here.
        if (comment == null || Boolean.TRUE.equals(comment.getDeleted())) {
            return ResponseUtil.badArgumentValue();
        }
        if (comment.getAdminContent() != null && !comment.getAdminContent().isEmpty()) {
            return ResponseUtil.fail(GoodsServiceResponseCode.ORDER_REPLY_EXIST,
                    "the comment has already been replied to");
        }
        comment.setAdminContent(body.getContent());
        comment.setUpdateTime(LocalDateTime.now());
        if (commentService.updateById(comment) == 0) {
            return ResponseUtil.updatedDataFailed();
        }
        return ResponseUtil.ok();
    }

    /** Body of {@code POST /reply}: {@code {"commentId": 1, "content": "..."}}. */
    public static class ReplyRequest {
        private Integer commentId;
        private String content;

        public Integer getCommentId() {
            return commentId;
        }

        public void setCommentId(Integer commentId) {
            this.commentId = commentId;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
