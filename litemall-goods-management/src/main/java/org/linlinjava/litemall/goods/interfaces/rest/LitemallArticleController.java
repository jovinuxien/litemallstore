package org.linlinjava.litemall.goods.interfaces.rest;

import jakarta.validation.constraints.NotNull;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.content.ArticleService;
import org.linlinjava.litemall.goods.domain.model.dto.goods.GoodsServiceResponseCode;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Customer article CMS reads (anonymous — {@code /srv/article/**} is on
 * public-paths). Contract: {@code docs/handoff-content-endpoints.md} §1.
 */
@RestController
@RequestMapping("/srv/article")
@Validated
public class LitemallArticleController {

    private final ArticleService articleService;

    public LitemallArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping("/list")
    public Object list(@RequestParam(required = false) Integer categoryId,
                       @RequestParam(defaultValue = "false") boolean hotOnly,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeLimit = limit == null || limit < 1 ? 10 : Math.min(limit, 50);
        return ResponseUtil.ok(articleService.customerList(categoryId, hotOnly, safePage, safeLimit));
    }

    /**
     * Published article incl. sanitized content; bumps view_count atomically
     * per hit. Missing/hidden/deleted → errno 643 (ARTICLE_NOT_AVAILABLE).
     */
    @GetMapping("/detail")
    public Object detail(@NotNull Integer id) {
        Map<String, Object> article = articleService.customerDetail(id);
        if (article == null) {
            return ResponseUtil.fail(GoodsServiceResponseCode.ARTICLE_NOT_AVAILABLE,
                    "article not available");
        }
        return ResponseUtil.ok(article);
    }

    @GetMapping("/categories")
    public Object categories() {
        List<Map<String, Object>> list = articleService.categories();
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        return ResponseUtil.ok(data);
    }
}
