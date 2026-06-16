package org.linlinjava.litemall.goods.application.comment;

import org.linlinjava.litemall.db.domain.LitemallComment;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.service.LitemallCommentService;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anonymous customer read queries for product reviews (litemall-wx-api {@code WxCommentController}
 * parity — read paths only; posting a review is authenticated and left as an order/user follow-up).
 * Mirrors the wx vo: each comment carries its author's {@code userInfo} (nickName/avatarUrl) resolved
 * from {@code litemall-db}. See {@code BrandQueryService} for the altitude note.
 */
@Service
public class CommentQueryService {

    private final LitemallCommentService commentService;
    private final LitemallUserService userService;

    public CommentQueryService(LitemallCommentService commentService,
                               LitemallUserService userService) {
        this.commentService = commentService;
        this.userService = userService;
    }

    /** Mapped comment vos plus the raw PageHelper page (so the controller can report the true total). */
    public record CommentList(List<Map<String, Object>> voList, List<LitemallComment> page) {}

    /**
     * Reviews for {@code valueId} (type 0 = goods), {@code showType} 0 = all / 1 = with-picture.
     * Each vo carries addTime/content/adminContent/picList/star + the author's userInfo, matching wx.
     */
    public CommentList list(Byte type, Integer valueId, Integer showType, Integer page, Integer limit) {
        List<LitemallComment> commentList = commentService.query(type, valueId, showType, page, limit);
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
        return new CommentList(voList, commentList);
    }

    /** {allCount, hasPicCount} for a target, as wx returns. */
    public Map<String, Object> count(Byte type, Integer valueId) {
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("allCount", commentService.count(type, valueId, 0));
        entity.put("hasPicCount", commentService.count(type, valueId, 1));
        return entity;
    }

    /** Minimal public author info (nickName/avatarUrl); anonymous/missing user → empty fields. */
    private Map<String, Object> userInfo(Integer userId) {
        Map<String, Object> info = new LinkedHashMap<>();
        LitemallUser user = userId == null ? null : userService.findById(userId);
        info.put("nickName", user != null ? user.getNickname() : "");
        info.put("avatarUrl", user != null ? user.getAvatar() : "");
        return info;
    }
}
