package org.linlinjava.litemall.promotion.interfaces.rest;

import org.linlinjava.litemall.promotion.application.LitemallPromotionOrchestratorService;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCombinationAggregate;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;
import org.linlinjava.litemall.promotion.interfaces.dtos.CombinationDtoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Customer-facing read of active combination (group-buy) campaigns. Joining a
 * group (participation/pink) is owned by litemall-order, not this service.
 */
@RestController
@RequestMapping("/srv/promotion/combination")
public class LitemallCombinationController {

    private final LitemallPromotionOrchestratorService orchestratorService;

    public LitemallCombinationController(LitemallPromotionOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @GetMapping("/active")
    public ResponseEntity<List<CombinationDtoResponse>> getActiveCombinations() {
        List<CombinationDtoResponse> response = orchestratorService.getCombinationService()
                .getActiveCombinations().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{combinationId}")
    public ResponseEntity<CombinationDtoResponse> getCombination(@PathVariable Integer combinationId) {
        return orchestratorService.getCombinationService()
                .getCombination(new LitemallCombinationId(combinationId))
                .map(c -> ResponseEntity.ok(toDto(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    private CombinationDtoResponse toDto(LitemallCombinationAggregate c) {
        return CombinationDtoResponse.builder()
                .combinationId(c.getCombinationId() != null ? c.getCombinationId().getId() : null)
                .goodsId(c.getGoodsId())
                .title(c.getTitle())
                .picUrl(c.getPicUrl())
                .combinationPrice(c.getCombinationPrice() != null ? c.getCombinationPrice().getAmount() : null)
                .originalPrice(c.getOriginalPrice() != null ? c.getOriginalPrice().getAmount() : null)
                .requiredMembers(c.getRequiredMembers())
                .status(c.getStatus() != null ? c.getStatus().getDisplayName() : null)
                .startTime(c.getStartTime())
                .endTime(c.getEndTime())
                .build();
    }
}
