package org.linlinjava.litemall.goods.interfaces.rest.admin;

import jakarta.validation.constraints.NotNull;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallPromoCandidate;
import org.linlinjava.litemall.goods.application.deals.AutoDailyDealTask;
import org.linlinjava.litemall.goods.application.promo.PromoCandidateAdminService;
import org.linlinjava.litemall.goods.application.insight.ArrivalInsightService;
import org.linlinjava.litemall.goods.application.insight.CategoryMarginService;
import org.linlinjava.litemall.goods.application.insight.GovernanceResult;
import org.linlinjava.litemall.goods.application.insight.InsightService;
import org.linlinjava.litemall.goods.application.insight.MarginBasisService;
import org.linlinjava.litemall.goods.application.insight.RetirementAdminService;
import org.linlinjava.litemall.goods.application.inventoryflow.RetirementExecutor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Wave-12/14 CJ inventory admin surface ({@code /srv/private/admin/insight/**} —
 * machine token + ROLE_ADMIN via the svcsecurity default admin path, rides gateway-admin's
 * {@code /srv/**} catch-all; no security/gateway config change needed). Serves the Wave-12
 * insight CONTRACT plus the Wave-14 governance CONTRACT (retire candidates, arrivals windows,
 * per-category margin overrides + simulator) for the gateway-admin SPA. Request paths read
 * local tables and memoized rollups only — never the CJ API. Errnos: 650/651/652 ride the
 * flash-deal author path on approve; 653 = no proposed candidate / already decided (deal AND
 * retire flavors). The two {@code /run} POSTs are manual triggers for the scheduled jobs
 * (dev acceptance / admin override) — same code path as the crons, honest summaries out.
 */
@RestController
@RequestMapping("/srv/private/admin/insight")
@Validated
public class AdminInsightController {

    public static class ApproveRequest {
        public BigDecimal dealPrice;
        public LocalDateTime startTime;
        public LocalDateTime stopTime;
        public Integer stock;
    }

    public static class RetireApproveRequest {
        public List<Integer> goodsIds;
        public LocalDate executeOn;
    }

    public static class MarginRequest {
        public BigDecimal margin;
    }

    public static class MarginBasisRequest {
        public List<Integer> goodsIds;
        public List<Integer> categoryIds;
    }

    /** Wave-19 dismiss/consume body: which kind's proposal, optionally which day, ref on consume. */
    public static class PromoDecisionRequest {
        public String kind;
        public LocalDate day;
        public Integer refId;
    }

    private final InsightService insightService;
    private final RetirementAdminService retirementAdminService;
    private final ArrivalInsightService arrivalInsightService;
    private final CategoryMarginService categoryMarginService;
    private final RetirementExecutor retirementExecutor;
    private final AutoDailyDealTask autoDailyDealTask;
    private final MarginBasisService marginBasisService;
    private final PromoCandidateAdminService promoCandidateAdminService;

    public AdminInsightController(InsightService insightService,
                                  RetirementAdminService retirementAdminService,
                                  ArrivalInsightService arrivalInsightService,
                                  CategoryMarginService categoryMarginService,
                                  RetirementExecutor retirementExecutor,
                                  AutoDailyDealTask autoDailyDealTask,
                                  MarginBasisService marginBasisService,
                                  PromoCandidateAdminService promoCandidateAdminService) {
        this.insightService = insightService;
        this.retirementAdminService = retirementAdminService;
        this.arrivalInsightService = arrivalInsightService;
        this.categoryMarginService = categoryMarginService;
        this.retirementExecutor = retirementExecutor;
        this.autoDailyDealTask = autoDailyDealTask;
        this.marginBasisService = marginBasisService;
        this.promoCandidateAdminService = promoCandidateAdminService;
    }

    /**
     * Wave-18 coupon margin-guard basis. Service-facing (promotion-service calls it with its
     * machine token + a forwarded {@code X-User-Roles: ROLE_ADMIN} — the established sister-
     * service recipe for admin-prefixed paths), rides the same svcsecurity admin gate as the
     * rest of this surface so captured costs stay off every customer-reachable path.
     * Contract: {@code docs/handoff-coupon-margin-basis.md}.
     */
    @PostMapping("/margin-basis")
    public Object marginBasis(@RequestBody(required = false) MarginBasisRequest body) {
        List<Integer> goodsIds = body == null ? null : body.goodsIds;
        List<Integer> categoryIds = body == null ? null : body.categoryIds;
        if (goodsIds != null && goodsIds.size() > MarginBasisService.MAX_GOODS_IDS) {
            return ResponseUtil.badArgument();
        }
        if (categoryIds != null && categoryIds.size() > MarginBasisService.MAX_CATEGORY_IDS) {
            return ResponseUtil.badArgument();
        }
        return ResponseUtil.ok(marginBasisService.basis(goodsIds, categoryIds));
    }

    @GetMapping("/categories")
    public Object categories() {
        return ResponseUtil.ok(insightService.categories());
    }

    @GetMapping("/goods/list")
    public Object goodsList(@RequestParam(required = false) Integer categoryId,
                            @RequestParam(required = false) String sort,
                            @RequestParam(required = false) String order,
                            @RequestParam(defaultValue = "1") Integer page,
                            @RequestParam(defaultValue = "20") Integer limit) {
        return ResponseUtil.ok(insightService.goodsList(categoryId, sort, order, page, limit));
    }

    @GetMapping("/goods/{id}")
    public Object goodsInsight(@NotNull @PathVariable Integer id) {
        Map<String, Object> data = insightService.goodsInsight(id);
        if (data == null) {
            return ResponseUtil.badArgumentValue();
        }
        return ResponseUtil.ok(data);
    }

    @GetMapping("/deal-candidates")
    public Object dealCandidates(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return ResponseUtil.ok(insightService.dealCandidates(day));
    }

    @PostMapping("/deal-candidates/{goodsId}/approve")
    public Object approve(@NotNull @PathVariable Integer goodsId,
                          @RequestBody ApproveRequest request) {
        return toResponse(insightService.approve(
                goodsId, request.dealPrice, request.startTime, request.stopTime, request.stock));
    }

    @PostMapping("/deal-candidates/{goodsId}/dismiss")
    public Object dismiss(@NotNull @PathVariable Integer goodsId) {
        return toResponse(insightService.dismiss(goodsId));
    }

    // ---- Wave 14: retirement --------------------------------------------------------------------

    @GetMapping("/retire-candidates")
    public Object retireCandidates(@RequestParam(required = false) String status) {
        return toResponse(retirementAdminService.list(status));
    }

    @PostMapping("/retire-candidates/approve")
    public Object retireApprove(@RequestBody RetireApproveRequest request) {
        return toResponse(retirementAdminService.approve(request.goodsIds, request.executeOn));
    }

    @PostMapping("/retire-candidates/{goodsId}/dismiss")
    public Object retireDismiss(@NotNull @PathVariable Integer goodsId) {
        return toResponse(retirementAdminService.dismiss(goodsId));
    }

    /** Manual trigger of the 02:00 executor pass (dev acceptance / admin override). */
    @PostMapping("/retire/run")
    public Object retireRun() {
        return ResponseUtil.ok(retirementExecutor.execute(LocalDate.now()));
    }

    // ---- Wave 14: arrivals ----------------------------------------------------------------------

    @GetMapping("/arrivals")
    public Object arrivals(@RequestParam(required = false) Integer runs) {
        return ResponseUtil.ok(arrivalInsightService.arrivals(runs));
    }

    // ---- Wave 14: per-category margins ----------------------------------------------------------

    @GetMapping("/margin-overrides")
    public Object marginOverrides() {
        return toResponse(categoryMarginService.overrides());
    }

    @PutMapping("/categories/{id}/margin")
    public Object putMargin(@NotNull @PathVariable Integer id, @RequestBody MarginRequest request) {
        return toResponse(categoryMarginService.put(id, request.margin));
    }

    @DeleteMapping("/categories/{id}/margin")
    public Object deleteMargin(@NotNull @PathVariable Integer id) {
        return toResponse(categoryMarginService.delete(id));
    }

    @GetMapping("/categories/{id}/simulate")
    public Object simulate(@NotNull @PathVariable Integer id, @RequestParam BigDecimal margin) {
        return toResponse(categoryMarginService.simulate(id, margin));
    }

    // ---- Wave 14: auto daily deals --------------------------------------------------------------

    /** Manual trigger of the 05:00 auto-deal pass (dev acceptance / admin override). */
    @PostMapping("/deals/auto-daily/run")
    public Object autoDailyRun() {
        return ResponseUtil.ok(autoDailyDealTask.run(LocalDate.now(), LocalDateTime.now()));
    }

    // ---- Wave 19: promo candidates --------------------------------------------------------------

    @GetMapping("/promo-candidates")
    public Object promoCandidates(
            @RequestParam String kind,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            @RequestParam(required = false) String status) {
        if (!isPromoKind(kind)) {
            return ResponseUtil.badArgumentValue();
        }
        return ResponseUtil.ok(promoCandidateAdminService.list(kind, day, status));
    }

    @PostMapping("/promo-candidates/{goodsId}/dismiss")
    public Object promoDismiss(@NotNull @PathVariable Integer goodsId,
                               @RequestBody PromoDecisionRequest request) {
        if (request == null || !isPromoKind(request.kind)) {
            return ResponseUtil.badArgumentValue();
        }
        return toResponse(promoCandidateAdminService.dismiss(request.kind, goodsId, request.day));
    }

    @PostMapping("/promo-candidates/{goodsId}/consume")
    public Object promoConsume(@NotNull @PathVariable Integer goodsId,
                               @RequestBody PromoDecisionRequest request) {
        if (request == null || !isPromoKind(request.kind)) {
            return ResponseUtil.badArgumentValue();
        }
        return toResponse(promoCandidateAdminService.consume(
                request.kind, goodsId, request.day, request.refId));
    }

    /** Manual trigger of the 04:30 promo-candidate scoring (dev acceptance / admin re-run). */
    @PostMapping("/promo-candidates/run")
    public Object promoRun(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return ResponseUtil.ok(promoCandidateAdminService.run(day));
    }

    private static boolean isPromoKind(String kind) {
        return LitemallPromoCandidate.KIND_COUPON.equals(kind)
                || LitemallPromoCandidate.KIND_GROUPON.equals(kind);
    }

    // ---- shared ---------------------------------------------------------------------------------

    private static Object toResponse(InsightService.CandidateActionResult result) {
        if (result.error() != null) {
            return ResponseUtil.fail(result.errno(), result.error());
        }
        return ResponseUtil.ok(result.data());
    }

    private static Object toResponse(GovernanceResult result) {
        if (result.error() != null) {
            return ResponseUtil.fail(result.errno(), result.error());
        }
        return ResponseUtil.ok(result.data());
    }
}
