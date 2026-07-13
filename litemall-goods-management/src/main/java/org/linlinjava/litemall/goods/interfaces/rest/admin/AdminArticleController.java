package org.linlinjava.litemall.goods.interfaces.rest.admin;

import jakarta.validation.constraints.NotNull;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallArticle;
import org.linlinjava.litemall.db.domain.LitemallArticleCategory;
import org.linlinjava.litemall.goods.application.content.ArticleService;
import org.linlinjava.litemall.goods.domain.model.dto.goods.GoodsServiceResponseCode;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Admin article CMS CRUD ({@code /srv/private/admin/**} → machine token +
 * ROLE_ADMIN via litemall-svcsecurity). Contract:
 * {@code docs/handoff-content-endpoints.md} §1.
 */
@RestController
@RequestMapping("/srv/private/admin/article")
@Validated
public class AdminArticleController {

    private static final Set<String> STATUSES =
            Set.of(LitemallArticle.STATUS_PUBLISHED, LitemallArticle.STATUS_HIDDEN);

    private final ArticleService articleService;

    public AdminArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping("/list")
    public Object list(@RequestParam(required = false) String title,
                       @RequestParam(required = false) Integer categoryId,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit) {
        if (status != null && !status.isEmpty() && !STATUSES.contains(status)) {
            return ResponseUtil.badArgumentValue();
        }
        int safePage = page == null || page < 1 ? 1 : page;
        int safeLimit = limit == null || limit < 1 ? 10 : Math.min(limit, 100);
        return ResponseUtil.ok(articleService.adminList(title, categoryId, status, safePage, safeLimit));
    }

    @GetMapping("/read")
    public Object read(@NotNull Integer id) {
        LitemallArticle article = articleService.adminRead(id);
        if (article == null) {
            return ResponseUtil.badArgumentValue();
        }
        return ResponseUtil.ok(article);
    }

    @PostMapping("/create")
    public Object create(@RequestBody LitemallArticle article) {
        Object error = validate(article, false);
        if (error != null) {
            return error;
        }
        articleService.create(article);
        return ResponseUtil.ok(article);
    }

    @PostMapping("/update")
    public Object update(@RequestBody LitemallArticle article) {
        if (article.getId() == null) {
            return ResponseUtil.badArgument();
        }
        Object error = validate(article, true);
        if (error != null) {
            return error;
        }
        if (articleService.update(article) == 0) {
            return ResponseUtil.updatedDataFailed();
        }
        return ResponseUtil.ok();
    }

    @PostMapping("/delete")
    public Object delete(@RequestBody LitemallArticle article) {
        if (article.getId() == null) {
            return ResponseUtil.badArgument();
        }
        articleService.delete(article.getId());
        return ResponseUtil.ok();
    }

    // ---------------- categories ----------------

    @GetMapping("/category/list")
    public Object categoryList() {
        return ResponseUtil.okList(articleService.adminCategoryList());
    }

    @PostMapping("/category/create")
    public Object categoryCreate(@RequestBody LitemallArticleCategory category) {
        if (category.getName() == null || category.getName().isBlank()
                || category.getName().length() > 63) {
            return ResponseUtil.badArgument();
        }
        articleService.categoryCreate(category);
        return ResponseUtil.ok(category);
    }

    @PostMapping("/category/update")
    public Object categoryUpdate(@RequestBody LitemallArticleCategory category) {
        if (category.getId() == null) {
            return ResponseUtil.badArgument();
        }
        if (category.getName() != null
                && (category.getName().isBlank() || category.getName().length() > 63)) {
            return ResponseUtil.badArgument();
        }
        if (articleService.categoryUpdate(category) == 0) {
            return ResponseUtil.updatedDataFailed();
        }
        return ResponseUtil.ok();
    }

    /** Refused while articles still reference the category → errno 641. */
    @PostMapping("/category/delete")
    public Object categoryDelete(@RequestBody LitemallArticleCategory category) {
        Integer id = category.getId();
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        int references = articleService.categoryReferenceCount(id);
        if (references > 0) {
            return ResponseUtil.fail(GoodsServiceResponseCode.CONTENT_CONFLICT,
                    "category still referenced by " + references + " article(s)");
        }
        articleService.categoryDelete(id);
        return ResponseUtil.ok();
    }

    // ---------------- helpers ----------------

    private Object validate(LitemallArticle article, boolean isUpdate) {
        if (!isUpdate && (article.getTitle() == null || article.getTitle().isBlank())) {
            return ResponseUtil.badArgument();
        }
        if (article.getTitle() != null
                && (article.getTitle().isBlank() || article.getTitle().length() > 255)) {
            return ResponseUtil.badArgument();
        }
        if (article.getStatus() != null && !STATUSES.contains(article.getStatus())) {
            return ResponseUtil.badArgumentValue();
        }
        if (article.getSummary() != null && article.getSummary().length() > 511) {
            return ResponseUtil.badArgument();
        }
        Integer categoryId = article.getCategoryId();
        if (categoryId != null && categoryId != 0 && articleService.findCategory(categoryId) == null) {
            return ResponseUtil.badArgumentValue();
        }
        return null;
    }
}
