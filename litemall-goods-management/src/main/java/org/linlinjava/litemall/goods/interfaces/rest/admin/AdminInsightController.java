package org.linlinjava.litemall.goods.interfaces.rest.admin;

import jakarta.validation.constraints.NotNull;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.insight.InsightService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Wave-12 CJ inventory-intelligence admin surface ({@code /srv/private/admin/insight/**} —
 * machine token + ROLE_ADMIN via the svcsecurity default admin path, rides gateway-admin's
 * {@code /srv/**} catch-all; no security/gateway config change needed). Serves the Wave-12
 * CONTRACT for the gateway-admin SPA. Request paths read local tables and memoized rollups
 * only — never the CJ API. Errnos: 650/651/652 ride the flash-deal author path on approve;
 * 653 = no proposed candidate / already decided.
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

    private final InsightService insightService;

    public AdminInsightController(InsightService insightService) {
        this.insightService = insightService;
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

    private static Object toResponse(InsightService.CandidateActionResult result) {
        if (result.error() != null) {
            return ResponseUtil.fail(result.errno(), result.error());
        }
        return ResponseUtil.ok(result.data());
    }
}
