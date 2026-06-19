package org.linlinjava.litemall.goods.application.issue;

import org.linlinjava.litemall.db.domain.LitemallIssue;
import org.linlinjava.litemall.db.service.LitemallIssueService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Anonymous customer read queries for help-center issues/FAQ (litemall-wx-api
 * {@code WxIssueController} parity). Read-only catalog-adjacent list → wx contract; see
 * {@link org.linlinjava.litemall.goods.application.brand.BrandQueryService} for the altitude note.
 */
@Service
public class IssueQueryService {

    private final LitemallIssueService issueService;

    public IssueQueryService(LitemallIssueService issueService) {
        this.issueService = issueService;
    }

    /** Paginated FAQ list, optionally filtered by question text (PageHelper-backed). */
    public List<LitemallIssue> list(String question, Integer page, Integer limit, String sort, String order) {
        return issueService.querySelective(question, page, limit, sort, order);
    }
}
