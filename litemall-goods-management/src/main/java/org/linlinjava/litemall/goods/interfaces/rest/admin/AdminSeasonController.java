package org.linlinjava.litemall.goods.interfaces.rest.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallSeasonRule;
import org.linlinjava.litemall.goods.application.season.SeasonAdminService;
import org.linlinjava.litemall.goods.application.season.SeasonScoringService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin surface for seasonal candidacy, under the existing insight routing.
 *
 * <p>Publishing is automatic, so there is deliberately no "approve" here: the two decisions an
 * admin makes are to veto a product for a season and to undo that. Rules are editable at runtime
 * because weights that could only change by redeploy would not really be configuration.
 */
@RestController
@RequestMapping("/srv/private/admin/insight")
public class AdminSeasonController {

    /** Errno for a decision that matched no row — the project's lost-race code. */
    private static final int ERRNO_LOST_RACE = 653;

    private final SeasonAdminService adminService;
    private final SeasonScoringService scoringService;

    public AdminSeasonController(SeasonAdminService adminService,
                                 SeasonScoringService scoringService) {
        this.adminService = adminService;
        this.scoringService = scoringService;
    }

    @GetMapping("/season-candidates")
    public Object candidates(@RequestParam(value = "season") String season,
                             @RequestParam(value = "status", required = false) String status,
                             @RequestParam(value = "day", required = false) String day) {
        if (season == null || season.isBlank()) {
            return ResponseUtil.badArgumentValue();
        }
        LocalDate parsedDay;
        try {
            parsedDay = (day == null || day.isBlank()) ? null : LocalDate.parse(day);
        } catch (RuntimeException ex) {
            return ResponseUtil.badArgumentValue();
        }
        return ResponseUtil.ok(adminService.candidates(season, status, parsedDay));
    }

    @PostMapping("/season-candidates/{goodsId}/dismiss")
    public Object dismiss(@PathVariable Integer goodsId, @RequestBody SeasonDecisionRequest request) {
        return decide(goodsId, request, true);
    }

    @PostMapping("/season-candidates/{goodsId}/restore")
    public Object restore(@PathVariable Integer goodsId, @RequestBody SeasonDecisionRequest request) {
        return decide(goodsId, request, false);
    }

    private Object decide(Integer goodsId, SeasonDecisionRequest request, boolean dismiss) {
        if (goodsId == null || request == null || request.season == null || request.season.isBlank()) {
            return ResponseUtil.badArgumentValue();
        }
        LocalDate day;
        try {
            day = (request.day == null || request.day.isBlank()) ? null : LocalDate.parse(request.day);
        } catch (RuntimeException ex) {
            return ResponseUtil.badArgumentValue();
        }
        SeasonAdminService.Outcome outcome = dismiss
                ? adminService.dismiss(request.season, goodsId, day)
                : adminService.restore(request.season, goodsId, day);
        return switch (outcome) {
            case OK -> ResponseUtil.ok();
            case NOT_FOUND -> ResponseUtil.badArgumentValue();
            case LOST_RACE -> ResponseUtil.fail(ERRNO_LOST_RACE,
                    "this candidate was already decided or no longer exists");
        };
    }

    @GetMapping("/season-rules")
    public Object rules() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LitemallSeasonRule rule : adminService.rules()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("seasonKey", rule.getSeasonKey());
            view.put("name", rule.getName());
            view.put("windowStartMd", rule.getWindowStartMd());
            view.put("windowEndMd", rule.getWindowEndMd());
            view.put("terms", rule.getTerms());
            view.put("categoryIds", rule.getCategoryIds());
            view.put("priceMin", rule.getPriceMin());
            view.put("priceMax", rule.getPriceMax());
            view.put("weights", rule.getWeights());
            view.put("enabled", rule.getEnabled());
            list.add(view);
        }
        return ResponseUtil.okList(list);
    }

    @PutMapping("/season-rules/{key}")
    public Object updateRule(@PathVariable String key, @RequestBody SeasonRuleRequest request) {
        if (key == null || key.isBlank() || request == null) {
            return ResponseUtil.badArgumentValue();
        }
        LitemallSeasonRule patch = new LitemallSeasonRule();
        patch.setSeasonKey(key);
        patch.setName(request.name);
        patch.setWindowStartMd(request.windowStartMd);
        patch.setWindowEndMd(request.windowEndMd);
        patch.setTerms(request.terms);
        patch.setCategoryIds(request.categoryIds);
        patch.setPriceMin(request.priceMin);
        patch.setPriceMax(request.priceMax);
        patch.setWeights(request.weights);
        patch.setEnabled(request.enabled);
        return adminService.updateRule(patch) ? ResponseUtil.ok() : ResponseUtil.badArgumentValue();
    }

    /**
     * Score every season now instead of waiting for 04:35 — the operational hook the deploy uses
     * to put members in the index BEFORE the full reindex, so the field's mapping materialises.
     */
    @PostMapping("/season-candidates/run")
    public Object run() {
        List<SeasonScoringService.SeasonRunResult> results = scoringService.scoreAll();
        List<Map<String, Object>> list = new ArrayList<>();
        for (SeasonScoringService.SeasonRunResult result : results) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("seasonKey", result.seasonKey());
            view.put("scanned", result.scanned());
            view.put("scored", result.scored());
            view.put("published", result.published());
            view.put("droppedByCap", result.dropped());
            view.put("rejections", result.rejections());
            list.add(view);
        }
        return ResponseUtil.ok(Map.of("seasons", list));
    }

    /** Body for dismiss/restore. */
    public static class SeasonDecisionRequest {
        public String season;
        public String day;
    }

    /** Body for a rule patch; every field is optional and null means "leave it alone". */
    public static class SeasonRuleRequest {
        public String name;
        public String windowStartMd;
        public String windowEndMd;
        public String terms;
        public String categoryIds;
        public BigDecimal priceMin;
        public BigDecimal priceMax;
        public String weights;
        public Boolean enabled;
    }
}
