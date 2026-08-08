package org.linlinjava.litemall.goods.application.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.db.dao.PageMapper;
import org.linlinjava.litemall.db.domain.LitemallPage;
import org.linlinjava.litemall.goods.domain.content.PageConfigValidator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DIY-page application service (palette v1, V36). Normative contract:
 * {@code docs/spec-page-palette-v1.md}.
 *
 * <p>The single-active-home invariant is enforced IN SCHEMA (stored generated
 * column + UNIQUE index); {@link #activate} demotes-then-promotes in one
 * transaction and translates the duplicate-key of a lost concurrent race into
 * {@link ActivateOutcome#CONFLICT} (errno 641 at the edge).
 */
@Service
public class PageService {

    public enum ActivateOutcome { OK, NOT_FOUND, CONFLICT }

    private final Log logger = LogFactory.getLog(PageService.class);

    private final PageMapper pageMapper;
    private final ObjectMapper objectMapper;
    private final PageConfigValidator validator;

    public PageService(PageMapper pageMapper, ObjectMapper objectMapper) {
        this.pageMapper = pageMapper;
        this.objectMapper = objectMapper;
        this.validator = new PageConfigValidator(objectMapper);
    }

    public PageConfigValidator.Result validateConfig(String rawJson) {
        return validator.validate(rawJson);
    }

    // ---------------- customer ----------------

    /** Active home PageView, or null (edge maps to errno 642 → SPA legacy-home fallback). */
    public Map<String, Object> activeHome() {
        LitemallPage page = pageMapper.selectActiveHome();
        return page == null ? null : toPageView(page);
    }

    /** Active page by id, or null (drafts are never served customer-side). */
    public Map<String, Object> activeById(Integer id) {
        LitemallPage page = pageMapper.selectActiveById(id);
        return page == null ? null : toPageView(page);
    }

    // ---------------- admin ----------------

    public Map<String, Object> adminList(String position, String status, String category,
                                         Boolean template, int page, int limit) {
        int offset = (page - 1) * limit;
        List<LitemallPage> rows = pageMapper.selectAdminPage(position, status, category, template, offset, limit);
        int total = pageMapper.countAdmin(position, status, category, template);
        List<Map<String, Object>> list = new ArrayList<>();
        for (LitemallPage p : rows) {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", p.getId());
            vo.put("name", p.getName());
            vo.put("position", p.getPosition());
            vo.put("category", p.getCategory());
            vo.put("status", p.getStatus());
            vo.put("isTemplate", Boolean.TRUE.equals(p.getIsTemplate()));
            vo.put("addTime", p.getAddTime());
            vo.put("updateTime", p.getUpdateTime());
            list.add(vo);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        return data;
    }

    public LitemallPage findById(Integer id) {
        return pageMapper.selectById(id);
    }

    /** Admin read: full row with the config parsed to an object (editor input). */
    public Map<String, Object> adminRead(LitemallPage page) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", page.getId());
        vo.put("name", page.getName());
        vo.put("position", page.getPosition());
        vo.put("category", page.getCategory());
        vo.put("status", page.getStatus());
        vo.put("isTemplate", Boolean.TRUE.equals(page.getIsTemplate()));
        vo.put("config", parseConfig(page));
        vo.put("addTime", page.getAddTime());
        vo.put("updateTime", page.getUpdateTime());
        return vo;
    }

    /** Insert as DRAFT (activation is always the separate, transactional step). */
    public LitemallPage create(String name, String position, String category, String normalizedConfig) {
        LocalDateTime now = LocalDateTime.now();
        LitemallPage page = new LitemallPage();
        page.setName(name);
        page.setPosition(position);
        page.setCategory(category);
        page.setConfig(normalizedConfig);
        page.setStatus(LitemallPage.STATUS_DRAFT);
        page.setIsTemplate(false);
        page.setAddTime(now);
        page.setUpdateTime(now);
        page.setDeleted(false);
        pageMapper.insert(page);
        return page;
    }

    /** Name/category/config patch; status and is_template never flow through here. */
    public int update(Integer id, String name, String category, String normalizedConfig) {
        LitemallPage patch = new LitemallPage();
        patch.setId(id);
        patch.setName(name);
        patch.setCategory(category);
        patch.setConfig(normalizedConfig);
        patch.setUpdateTime(LocalDateTime.now());
        return pageMapper.updateSelective(patch);
    }

    /**
     * Clone ANY page (template or not, active or not) into a fresh DRAFT:
     * name "Copy of &lt;name&gt;" (truncated to the 63-char column), position
     * always {@code custom}, category + config inherited. The copy is a NORMAL
     * page — {@code is_template} is never inherited (templates are seed-only)
     * and active status never travels (activation stays the separate,
     * transactional step). Returns null when the source is missing/deleted.
     */
    public LitemallPage clone(Integer id) {
        LitemallPage source = pageMapper.selectById(id);
        if (source == null) {
            return null;
        }
        String name = "Copy of " + source.getName();
        if (name.length() > 63) {
            name = name.substring(0, 63);
        }
        LocalDateTime now = LocalDateTime.now();
        LitemallPage copy = new LitemallPage();
        copy.setName(name);
        copy.setPosition(LitemallPage.POSITION_CUSTOM);
        copy.setCategory(source.getCategory());
        copy.setConfig(source.getConfig());
        copy.setStatus(LitemallPage.STATUS_DRAFT);
        copy.setIsTemplate(false);
        copy.setAddTime(now);
        copy.setUpdateTime(now);
        copy.setDeleted(false);
        pageMapper.insert(copy);
        return copy;
    }

    /**
     * Transactional home swap: activating a home page demotes the current
     * active home in the same TX; the schema's UNIQUE index turns a concurrent
     * double-activation into a duplicate-key → CONFLICT (641). Idempotent on an
     * already-active page.
     */
    @Transactional
    public ActivateOutcome activate(Integer id) {
        LitemallPage page = pageMapper.selectById(id);
        if (page == null) {
            return ActivateOutcome.NOT_FOUND;
        }
        if (LitemallPage.STATUS_ACTIVE.equals(page.getStatus())) {
            return ActivateOutcome.OK;
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            if (LitemallPage.POSITION_HOME.equals(page.getPosition())) {
                pageMapper.demoteActiveHome(now);
            }
            pageMapper.activate(id, now);
            return ActivateOutcome.OK;
        } catch (DuplicateKeyException e) {
            logger.warn("page activate " + id + " lost the single-active-home race", e);
            return ActivateOutcome.CONFLICT;
        }
    }

    public int deactivate(Integer id) {
        return pageMapper.deactivate(id, LocalDateTime.now());
    }

    /** Caller must have refused active-home deletion (errno 641) first. */
    public void delete(Integer id) {
        pageMapper.logicalDeleteById(id, LocalDateTime.now());
    }

    // ---------------- helpers ----------------

    /** Customer PageView per spec §4: components straight from the validated config. */
    private Map<String, Object> toPageView(LitemallPage page) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", page.getId());
        vo.put("name", page.getName());
        vo.put("position", page.getPosition());
        vo.put("components", parseConfig(page).getOrDefault("components", List.of()));
        vo.put("updateTime", page.getUpdateTime());
        return vo;
    }

    /** Config was validated at write time; a parse failure here is logged, never a 5xx. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(LitemallPage page) {
        try {
            JsonNode node = objectMapper.readTree(page.getConfig());
            return objectMapper.convertValue(node, Map.class);
        } catch (Exception e) {
            logger.error("page " + page.getId() + " carries unparseable config — serving empty components", e);
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("version", PageConfigValidator.VERSION);
            fallback.put("components", List.of());
            return fallback;
        }
    }
}
