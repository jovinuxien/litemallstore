package org.linlinjava.litemall.goods.interfaces.rest.admin;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallPage;
import org.linlinjava.litemall.goods.application.content.PageService;
import org.linlinjava.litemall.goods.domain.content.PageConfigValidator;
import org.linlinjava.litemall.goods.domain.model.dto.goods.GoodsServiceResponseCode;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Admin DIY-page surface ({@code /srv/private/admin/**} → machine token +
 * ROLE_ADMIN). Normative contract: {@code docs/spec-page-palette-v1.md} §5–§7.
 * The structured editor must be driven by {@code GET /palette}.
 */
@RestController
@RequestMapping("/srv/private/admin/page")
@Validated
public class AdminPageController {

    private static final Set<String> POSITIONS =
            Set.of(LitemallPage.POSITION_HOME, LitemallPage.POSITION_CUSTOM);
    private static final Set<String> STATUSES =
            Set.of(LitemallPage.STATUS_DRAFT, LitemallPage.STATUS_ACTIVE);
    private static final Set<String> CATEGORIES =
            Set.of(LitemallPage.CATEGORY_GENERAL, LitemallPage.CATEGORY_COUPON,
                    LitemallPage.CATEGORY_GROUPON);

    private final PageService pageService;

    public AdminPageController(PageService pageService) {
        this.pageService = pageService;
    }

    /** Upsert body: config arrives as a JSON object (JsonNode), not a string. */
    public static class PageUpsertRequest {
        private Integer id;
        private String name;
        private String position;
        private String category;
        private JsonNode config;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPosition() {
            return position;
        }

        public void setPosition(String position) {
            this.position = position;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public JsonNode getConfig() {
            return config;
        }

        public void setConfig(JsonNode config) {
            this.config = config;
        }
    }

    /** Id-only body for activate/deactivate/delete. */
    public static class PageIdRequest {
        private Integer id;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }
    }

    @GetMapping("/list")
    public Object list(@RequestParam(required = false) String position,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) Integer template,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit) {
        if (position != null && !position.isEmpty() && !POSITIONS.contains(position)) {
            return ResponseUtil.badArgumentValue();
        }
        if (status != null && !status.isEmpty() && !STATUSES.contains(status)) {
            return ResponseUtil.badArgumentValue();
        }
        if (category != null && !category.isEmpty() && !CATEGORIES.contains(category)) {
            return ResponseUtil.badArgumentValue();
        }
        if (template != null && template != 0 && template != 1) {
            return ResponseUtil.badArgumentValue();
        }
        Boolean templateFilter = template == null ? null : template == 1;
        int safePage = page == null || page < 1 ? 1 : page;
        int safeLimit = limit == null || limit < 1 ? 10 : Math.min(limit, 100);
        return ResponseUtil.ok(
                pageService.adminList(position, status, category, templateFilter, safePage, safeLimit));
    }

    /** Any status — this is also the draft-preview read. */
    @GetMapping("/read")
    public Object read(@NotNull Integer id) {
        LitemallPage page = pageService.findById(id);
        if (page == null) {
            return ResponseUtil.badArgumentValue();
        }
        return ResponseUtil.ok(pageService.adminRead(page));
    }

    /** Machine-readable component schema — build the editor from THIS. */
    @GetMapping("/palette")
    public Object palette() {
        return ResponseUtil.ok(PageConfigValidator.paletteSchema());
    }

    /** Always created as draft; palette violations → errno 640 naming the component. */
    @PostMapping("/create")
    public Object create(@RequestBody PageUpsertRequest body) {
        if (body.getName() == null || body.getName().isBlank() || body.getName().length() > 63) {
            return ResponseUtil.badArgument();
        }
        String position = body.getPosition() == null ? LitemallPage.POSITION_CUSTOM : body.getPosition();
        if (!POSITIONS.contains(position)) {
            return ResponseUtil.badArgumentValue();
        }
        String category = body.getCategory() == null ? LitemallPage.CATEGORY_GENERAL : body.getCategory();
        if (!CATEGORIES.contains(category)) {
            return ResponseUtil.fail(GoodsServiceResponseCode.PAGE_CONFIG_INVALID,
                    "category must be one of " + CATEGORIES);
        }
        if (body.getConfig() == null || body.getConfig().isNull()) {
            return ResponseUtil.badArgument();
        }
        PageConfigValidator.Result result = pageService.validateConfig(body.getConfig().toString());
        if (!result.isValid()) {
            return ResponseUtil.fail(GoodsServiceResponseCode.PAGE_CONFIG_INVALID, result.getError());
        }
        LitemallPage page = pageService.create(body.getName(), position, category, result.getNormalizedJson());
        return ResponseUtil.ok(pageService.adminRead(page));
    }

    /** Re-validates the config; position is immutable after create. */
    @PostMapping("/update")
    public Object update(@RequestBody PageUpsertRequest body) {
        if (body.getId() == null) {
            return ResponseUtil.badArgument();
        }
        if (pageService.findById(body.getId()) == null) {
            return ResponseUtil.badArgumentValue();
        }
        if (body.getName() != null && (body.getName().isBlank() || body.getName().length() > 63)) {
            return ResponseUtil.badArgument();
        }
        if (body.getCategory() != null && !CATEGORIES.contains(body.getCategory())) {
            return ResponseUtil.fail(GoodsServiceResponseCode.PAGE_CONFIG_INVALID,
                    "category must be one of " + CATEGORIES);
        }
        String normalized = null;
        if (body.getConfig() != null && !body.getConfig().isNull()) {
            PageConfigValidator.Result result = pageService.validateConfig(body.getConfig().toString());
            if (!result.isValid()) {
                return ResponseUtil.fail(GoodsServiceResponseCode.PAGE_CONFIG_INVALID, result.getError());
            }
            normalized = result.getNormalizedJson();
        }
        if (body.getName() == null && body.getCategory() == null && normalized == null) {
            return ResponseUtil.badArgument();
        }
        if (pageService.update(body.getId(), body.getName(), body.getCategory(), normalized) == 0) {
            return ResponseUtil.updatedDataFailed();
        }
        return ResponseUtil.ok();
    }

    /**
     * Clone ANY page (template or otherwise) into a fresh DRAFT copy —
     * "New from template" in admin. Position always {@code custom},
     * category + config inherited; {@code is_template} and active status are
     * never copied (clones are normal pages).
     */
    @PostMapping("/{id}/clone")
    public Object clone(@PathVariable("id") Integer id) {
        LitemallPage copy = pageService.clone(id);
        if (copy == null) {
            return ResponseUtil.badArgumentValue();
        }
        return ResponseUtil.ok(pageService.adminRead(copy));
    }

    /** Transactional home swap; a lost concurrent race → errno 641. */
    @PostMapping("/activate")
    public Object activate(@RequestBody PageIdRequest body) {
        if (body.getId() == null) {
            return ResponseUtil.badArgument();
        }
        return switch (pageService.activate(body.getId())) {
            case NOT_FOUND -> ResponseUtil.badArgumentValue();
            case CONFLICT -> ResponseUtil.fail(GoodsServiceResponseCode.CONTENT_CONFLICT,
                    "another home page was activated concurrently");
            case OK -> ResponseUtil.ok();
        };
    }

    @PostMapping("/deactivate")
    public Object deactivate(@RequestBody PageIdRequest body) {
        if (body.getId() == null) {
            return ResponseUtil.badArgument();
        }
        if (pageService.deactivate(body.getId()) == 0) {
            return ResponseUtil.badArgumentValue();
        }
        return ResponseUtil.ok();
    }

    /** Refuses the ACTIVE home (errno 641) — deactivate or activate a successor first. */
    @PostMapping("/delete")
    public Object delete(@RequestBody PageIdRequest body) {
        Integer id = body.getId();
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        LitemallPage page = pageService.findById(id);
        if (page == null) {
            return ResponseUtil.badArgumentValue();
        }
        if (LitemallPage.POSITION_HOME.equals(page.getPosition())
                && LitemallPage.STATUS_ACTIVE.equals(page.getStatus())) {
            return ResponseUtil.fail(GoodsServiceResponseCode.CONTENT_CONFLICT,
                    "cannot delete the active home page — deactivate it first");
        }
        pageService.delete(id);
        return ResponseUtil.ok();
    }
}
