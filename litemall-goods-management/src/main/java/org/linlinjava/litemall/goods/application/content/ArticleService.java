package org.linlinjava.litemall.goods.application.content;

import org.linlinjava.litemall.db.dao.ArticleCategoryMapper;
import org.linlinjava.litemall.db.dao.ArticleMapper;
import org.linlinjava.litemall.db.domain.LitemallArticle;
import org.linlinjava.litemall.db.domain.LitemallArticleCategory;
import org.linlinjava.litemall.goods.domain.content.HtmlSanitizer;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Article CMS application service (content subdomain, V36). Contracts:
 * {@code docs/handoff-content-endpoints.md} §1.
 *
 * <p>Write paths run every {@code content} through {@link HtmlSanitizer}
 * (clean-and-store), so read paths serve injectable HTML. {@code viewCount}
 * bumps are the mapper's atomic {@code view_count = view_count + 1} — never
 * read-modify-write.
 */
@Service
public class ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleCategoryMapper categoryMapper;

    public ArticleService(ArticleMapper articleMapper, ArticleCategoryMapper categoryMapper) {
        this.articleMapper = articleMapper;
        this.categoryMapper = categoryMapper;
    }

    // ---------------- customer ----------------

    public Map<String, Object> customerList(Integer categoryId, boolean hotOnly, int page, int limit) {
        int offset = (page - 1) * limit;
        List<LitemallArticle> rows = articleMapper.selectCustomerPage(categoryId, hotOnly, offset, limit);
        int total = articleMapper.countCustomer(categoryId, hotOnly);
        Map<Integer, String> names = categoryNames();
        List<Map<String, Object>> list = rows.stream().map(a -> listVo(a, names)).collect(Collectors.toList());
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        return data;
    }

    /**
     * Published article with content, bumping view_count atomically per hit
     * (accepted anonymous GET-with-UPDATE, crmeb-same). Null when missing,
     * hidden or deleted — the edge maps that to errno 643.
     */
    public Map<String, Object> customerDetail(Integer id) {
        LitemallArticle article = articleMapper.selectPublishedById(id);
        if (article == null) {
            return null;
        }
        articleMapper.incrementViewCount(id);
        Map<String, Object> vo = listVo(article, categoryNames());
        vo.put("content", article.getContent());
        // Reflect the bump this very hit just made.
        vo.put("viewCount", (article.getViewCount() == null ? 0 : article.getViewCount()) + 1);
        return vo;
    }

    public List<Map<String, Object>> categories() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LitemallArticleCategory c : categoryMapper.selectAll()) {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", c.getId());
            vo.put("name", c.getName());
            vo.put("sortOrder", c.getSortOrder());
            list.add(vo);
        }
        return list;
    }

    // ---------------- admin ----------------

    public Map<String, Object> adminList(String title, Integer categoryId, String status, int page, int limit) {
        int offset = (page - 1) * limit;
        List<LitemallArticle> rows = articleMapper.selectAdminPage(title, categoryId, status, offset, limit);
        int total = articleMapper.countAdmin(title, categoryId, status);
        Map<Integer, String> names = categoryNames();
        List<Map<String, Object>> list = rows.stream().map(a -> listVo(a, names)).collect(Collectors.toList());
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        return data;
    }

    public LitemallArticle adminRead(Integer id) {
        return articleMapper.selectById(id);
    }

    public LitemallArticleCategory findCategory(Integer id) {
        return categoryMapper.selectById(id);
    }

    public void create(LitemallArticle article) {
        LocalDateTime now = LocalDateTime.now();
        article.setContent(HtmlSanitizer.sanitize(article.getContent()));
        if (article.getStatus() == null) {
            article.setStatus(LitemallArticle.STATUS_PUBLISHED);
        }
        if (article.getCategoryId() == null) {
            article.setCategoryId(0);
        }
        if (article.getGoodsId() == null) {
            article.setGoodsId(0);
        }
        if (article.getIsHot() == null) {
            article.setIsHot(false);
        }
        if (article.getIsBanner() == null) {
            article.setIsBanner(false);
        }
        article.setViewCount(0);
        article.setAddTime(now);
        article.setUpdateTime(now);
        article.setDeleted(false);
        articleMapper.insert(article);
    }

    public int update(LitemallArticle article) {
        article.setContent(HtmlSanitizer.sanitize(article.getContent()));
        article.setViewCount(null); // never client-writable; bumps are atomic
        article.setUpdateTime(LocalDateTime.now());
        return articleMapper.updateSelective(article);
    }

    public void delete(Integer id) {
        articleMapper.logicalDeleteById(id, LocalDateTime.now());
    }

    // ---------------- categories (admin) ----------------

    public List<Map<String, Object>> adminCategoryList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LitemallArticleCategory c : categoryMapper.selectAll()) {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", c.getId());
            vo.put("name", c.getName());
            vo.put("sortOrder", c.getSortOrder());
            vo.put("articleCount", articleMapper.countByCategory(c.getId()));
            vo.put("addTime", c.getAddTime());
            vo.put("updateTime", c.getUpdateTime());
            list.add(vo);
        }
        return list;
    }

    public void categoryCreate(LitemallArticleCategory category) {
        LocalDateTime now = LocalDateTime.now();
        if (category.getSortOrder() == null) {
            category.setSortOrder(100);
        }
        category.setAddTime(now);
        category.setUpdateTime(now);
        category.setDeleted(false);
        categoryMapper.insert(category);
    }

    public int categoryUpdate(LitemallArticleCategory category) {
        category.setUpdateTime(LocalDateTime.now());
        return categoryMapper.updateSelective(category);
    }

    /** Non-deleted articles still referencing the category (0 = safe to delete). */
    public int categoryReferenceCount(Integer id) {
        return articleMapper.countByCategory(id);
    }

    public void categoryDelete(Integer id) {
        categoryMapper.logicalDeleteById(id, LocalDateTime.now());
    }

    // ---------------- helpers ----------------

    private Map<Integer, String> categoryNames() {
        return categoryMapper.selectAll().stream()
                .collect(Collectors.toMap(LitemallArticleCategory::getId,
                        LitemallArticleCategory::getName, (a, b) -> a));
    }

    private static Map<String, Object> listVo(LitemallArticle a, Map<Integer, String> categoryNames) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", a.getId());
        vo.put("categoryId", a.getCategoryId());
        vo.put("categoryName", categoryNames.getOrDefault(a.getCategoryId(), null));
        vo.put("title", a.getTitle());
        vo.put("summary", a.getSummary());
        vo.put("picUrl", a.getPicUrl());
        vo.put("status", a.getStatus());
        vo.put("isHot", a.getIsHot());
        vo.put("isBanner", a.getIsBanner());
        vo.put("goodsId", a.getGoodsId());
        vo.put("viewCount", a.getViewCount());
        vo.put("addTime", a.getAddTime());
        vo.put("updateTime", a.getUpdateTime());
        return vo;
    }

    /** Exposed for reuse where a Function is handier than the service. */
    public Function<LitemallArticle, Map<String, Object>> listVoMapper() {
        Map<Integer, String> names = categoryNames();
        return a -> listVo(a, names);
    }
}
