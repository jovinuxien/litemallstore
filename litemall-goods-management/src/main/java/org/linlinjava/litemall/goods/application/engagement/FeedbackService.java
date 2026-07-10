package org.linlinjava.litemall.goods.application.engagement;

import org.linlinjava.litemall.db.domain.LitemallFeedback;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.service.LitemallFeedbackService;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Customer feedback over the legacy {@code litemall_feedback} table
 * (litemall-wx-api {@code /wx/feedback} parity). {@code type} is the SPA's
 * free-form category ({@code feedback|complaint|bug|other}) stored as
 * {@code feed_type}; {@code status} 0 = unhandled, for the admin queue.
 */
@Service
public class FeedbackService {

    private final LitemallFeedbackService feedbackService;
    private final LitemallUserService userService;

    public FeedbackService(LitemallFeedbackService feedbackService,
                           LitemallUserService userService) {
        this.feedbackService = feedbackService;
        this.userService = userService;
    }

    public void submit(Integer userId, String type, String content, String mobile) {
        LitemallUser user = userService.findById(userId);
        LitemallFeedback feedback = new LitemallFeedback();
        feedback.setUserId(userId);
        feedback.setUsername(user != null ? user.getUsername() : String.valueOf(userId));
        feedback.setMobile(StringUtils.hasText(mobile) ? mobile
                : (user != null && StringUtils.hasText(user.getMobile()) ? user.getMobile() : ""));
        feedback.setFeedType(type);
        feedback.setContent(content);
        feedback.setStatus(0);
        feedback.setHasPicture(false);
        feedbackService.add(feedback);
    }
}
