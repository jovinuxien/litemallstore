package org.linlinjava.litemall.goods.interfaces.rest.admin;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.db.domain.LitemallCollect;
import org.linlinjava.litemall.db.domain.LitemallFeedback;
import org.linlinjava.litemall.db.domain.LitemallFootprint;
import org.linlinjava.litemall.db.service.LitemallCollectService;
import org.linlinjava.litemall.db.service.LitemallFeedbackService;
import org.linlinjava.litemall.db.service.LitemallFootprintService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin paging over the engagement tables ({@code litemall_collect} /
 * {@code litemall_footprint} / {@code litemall_feedback}), mirroring
 * {@link AdminAddressController} / {@link AdminUserController}: mounted under
 * {@code /srv/private/admin/**} (ROLE_ADMIN-gated by litemall-svcsecurity), an
 * optional {@code userId} filter, and {@link ResponseUtil#okList} paging. Unlike
 * the customer surface these are cross-user admin reads, so {@code userId} is a
 * filter param here (the caller is an admin, not the data owner).
 */
@RestController
@RequestMapping("/srv/private/admin")
@Validated
public class AdminEngagementController {

    private final LitemallCollectService collectService;
    private final LitemallFootprintService footprintService;
    private final LitemallFeedbackService feedbackService;

    public AdminEngagementController(LitemallCollectService collectService,
                                     LitemallFootprintService footprintService,
                                     LitemallFeedbackService feedbackService) {
        this.collectService = collectService;
        this.footprintService = footprintService;
        this.feedbackService = feedbackService;
    }

    @GetMapping("/collect/list")
    public Object collectList(String userId, String valueId,
                              @RequestParam(defaultValue = "1") Integer page,
                              @RequestParam(defaultValue = "10") Integer limit,
                              @Sort @RequestParam(defaultValue = "add_time") String sort,
                              @Order @RequestParam(defaultValue = "desc") String order) {
        List<LitemallCollect> list = collectService.querySelective(userId, valueId, page, limit, sort, order);
        return ResponseUtil.okList(list);
    }

    @GetMapping("/footprint/list")
    public Object footprintList(String userId, String goodsId,
                                @RequestParam(defaultValue = "1") Integer page,
                                @RequestParam(defaultValue = "10") Integer limit,
                                @Sort @RequestParam(defaultValue = "add_time") String sort,
                                @Order @RequestParam(defaultValue = "desc") String order) {
        List<LitemallFootprint> list = footprintService.querySelective(userId, goodsId, page, limit, sort, order);
        return ResponseUtil.okList(list);
    }

    @GetMapping("/feedback/list")
    public Object feedbackList(Integer userId, String username,
                               @RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "10") Integer limit,
                               @Sort @RequestParam(defaultValue = "add_time") String sort,
                               @Order @RequestParam(defaultValue = "desc") String order) {
        List<LitemallFeedback> list = feedbackService.querySelective(userId, username, page, limit, sort, order);
        return ResponseUtil.okList(list);
    }
}
