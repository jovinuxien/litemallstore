package org.linlinjava.litemall.goods.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.engagement.FeedbackService;
import org.linlinjava.litemall.goods.utils.UserContext;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Customer feedback ({@code /srv/feedback}, litemall-wx-api {@code /wx/feedback}
 * parity). Authenticated: NOT on the svcsecurity public-paths list; the
 * submitter is the gateway-injected {@code X-User-Id}.
 */
@RestController
@RequestMapping("/srv/feedback")
public class LitemallFeedbackController {

    private final FeedbackService feedbackService;

    public LitemallFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /** Body: {@code {content, mobile?, type}} — type ∈ feedback|complaint|bug|other. */
    @PostMapping("/submit")
    public Object submit(@RequestBody Map<String, Object> body) {
        Integer userId = UserContext.getUserIdAsInt();
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        String content = body.get("content") != null ? String.valueOf(body.get("content")) : null;
        String type = body.get("type") != null ? String.valueOf(body.get("type")) : "feedback";
        String mobile = body.get("mobile") != null ? String.valueOf(body.get("mobile")) : null;
        if (!StringUtils.hasText(content)) {
            return ResponseUtil.badArgumentValue();
        }
        feedbackService.submit(userId, type, content, mobile);
        return ResponseUtil.ok();
    }
}
