package org.linlinjava.litemall.promotion.interfaces.rest.legacy;

import org.linlinjava.litemall.promotion.application.LitemallPromotionOrchestratorService;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCombinationAggregate;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Legacy-path group-buy browse surface matching the customer SPA's
 * {@code contentApi.grouponList} contract ({@code /srv/groupon/list},
 * {@code GrouponItem} field names), backed by the combination vertical's
 * active campaign definitions. Canonical surface:
 * {@code /srv/promotion/combination/**}.
 */
@RestController
@RequestMapping("/srv/groupon")
public class LitemallGrouponLegacyController {

    private final LitemallPromotionOrchestratorService orchestratorService;

    public LitemallGrouponLegacyController(LitemallPromotionOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        List<LitemallCombinationAggregate> active =
                orchestratorService.getCombinationService().getActiveCombinations();
        int from = Math.min((page - 1) * limit, active.size());
        int to = Math.min(from + limit, active.size());
        List<Map<String, Object>> list = active.subList(from, to).stream()
                .map(this::toGrouponItem)
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", active.size());
        return ApiResponse.ok(data);
    }

    private Map<String, Object> toGrouponItem(LitemallCombinationAggregate c) {
        BigDecimal original = c.getOriginalPrice() != null ? c.getOriginalPrice().getAmount() : null;
        BigDecimal groupon = c.getCombinationPrice() != null ? c.getCombinationPrice().getAmount() : null;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", c.getCombinationId() != null ? c.getCombinationId().getId() : null);
        item.put("goodsId", c.getGoodsId());
        item.put("goodsName", c.getTitle());
        item.put("picUrl", c.getPicUrl());
        item.put("retailPrice", original);
        item.put("grouponPrice", groupon);
        item.put("discount", original != null && groupon != null ? original.subtract(groupon) : null);
        item.put("discountMember", c.getRequiredMembers());
        return item;
    }
}
