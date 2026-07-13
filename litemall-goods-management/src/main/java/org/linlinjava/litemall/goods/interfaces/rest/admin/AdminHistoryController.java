package org.linlinjava.litemall.goods.interfaces.rest.admin;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.db.domain.LitemallSearchHistory;
import org.linlinjava.litemall.db.service.LitemallSearchHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin search-history browsing, ported from litemall-admin-api ({@code admin.web.AdminHistoryController}).
 * Mounted under {@code /srv/private/admin/**} (ROLE_ADMIN-gated by litemall-svcsecurity).
 */
@RestController
@RequestMapping("/srv/private/admin/history")
@Validated
public class AdminHistoryController {
    private final Log logger = LogFactory.getLog(AdminHistoryController.class);

    @Autowired
    private LitemallSearchHistoryService searchHistoryService;

    @GetMapping("/list")
    public Object list(String userId, String keyword,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit,
                       @Sort @RequestParam(defaultValue = "add_time") String sort,
                       @Order @RequestParam(defaultValue = "desc") String order) {
        List<LitemallSearchHistory> historyList =
                searchHistoryService.querySelective(userId, keyword, page, limit, sort, order);
        return ResponseUtil.okList(historyList);
    }
}
