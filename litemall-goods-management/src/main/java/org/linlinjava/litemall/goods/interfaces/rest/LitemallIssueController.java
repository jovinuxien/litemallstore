package org.linlinjava.litemall.goods.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.goods.application.issue.IssueQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous customer help-center surface (litemall-wx-api {@code /wx/issue} parity), on
 * {@code /srv/issue}. Public per {@code litemall.svcsecurity.public-paths}.
 */
@RestController
@RequestMapping("/srv/issue")
public class LitemallIssueController {

    private final IssueQueryService issueQueryService;

    public LitemallIssueController(IssueQueryService issueQueryService) {
        this.issueQueryService = issueQueryService;
    }

    @GetMapping("/list")
    public Object list(String question,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer size,
                       @Sort @RequestParam(defaultValue = "add_time") String sort,
                       @Order @RequestParam(defaultValue = "desc") String order) {
        return ResponseUtil.okList(issueQueryService.list(question, page, size, sort, order));
    }
}
