package org.linlinjava.litemall.promotion.interfaces.rest.admin;

import org.linlinjava.litemall.promotion.application.LitemallPromotionOrchestratorService;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCombinationAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallActivateCombinationCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallDefineCombinationCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallExpireCombinationCommand;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.linlinjava.litemall.promotion.interfaces.dtos.CombinationManagerDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.PromotionOperationDtoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

import static org.linlinjava.litemall.promotion.interfaces.util.LitemallHttpResponseUtil.buildResponse;

/**
 * Admin management of combination (group-buy) campaign definitions. Under
 * {@code /srv/private/admin/**}, gated to {@code ROLE_ADMIN} by
 * litemall-svcsecurity. Participation/pink remains in litemall-order.
 */
@RestController
@RequestMapping("/srv/private/admin/promotion/combination")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
public class LitemallCombinationAdminController {

    private final LitemallPromotionOrchestratorService orchestratorService;

    public LitemallCombinationAdminController(LitemallPromotionOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @GetMapping("/list")
    public ResponseEntity<List<CombinationManagerDtoResponse>> list() {
        List<CombinationManagerDtoResponse> response = orchestratorService.getCombinationService()
                .listCombinations().stream()
                .map(this::toManagerDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{combinationId}")
    public ResponseEntity<CombinationManagerDtoResponse> get(@PathVariable Integer combinationId) {
        return orchestratorService.getCombinationService()
                .getCombination(new LitemallCombinationId(combinationId))
                .map(c -> ResponseEntity.ok(toManagerDto(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PromotionOperationDtoResponse> define(
            @RequestBody LitemallDefineCombinationCommand command) {
        LitemallPromotionOperationResult result = orchestratorService.defineCombination(command);
        return buildResponse(result);
    }

    @PostMapping("/{combinationId}/activate")
    public ResponseEntity<PromotionOperationDtoResponse> activate(@PathVariable Integer combinationId) {
        LitemallPromotionOperationResult result = orchestratorService.activateCombination(
                new LitemallActivateCombinationCommand(new LitemallCombinationId(combinationId)));
        return buildResponse(result);
    }

    @PostMapping("/{combinationId}/expire")
    public ResponseEntity<PromotionOperationDtoResponse> expire(@PathVariable Integer combinationId) {
        LitemallPromotionOperationResult result = orchestratorService.expireCombination(
                new LitemallExpireCombinationCommand(new LitemallCombinationId(combinationId)));
        return buildResponse(result);
    }

    private CombinationManagerDtoResponse toManagerDto(LitemallCombinationAggregate c) {
        return CombinationManagerDtoResponse.builder()
                .combinationId(c.getCombinationId() != null ? c.getCombinationId().getId() : null)
                .goodsId(c.getGoodsId())
                .title(c.getTitle())
                .picUrl(c.getPicUrl())
                .combinationPrice(c.getCombinationPrice() != null ? c.getCombinationPrice().getAmount() : null)
                .originalPrice(c.getOriginalPrice() != null ? c.getOriginalPrice().getAmount() : null)
                .requiredMembers(c.getRequiredMembers())
                .limitPerUser(c.getLimitPerUser())
                .status(c.getStatus() != null ? c.getStatus().getDisplayName() : null)
                .startTime(c.getStartTime())
                .endTime(c.getEndTime())
                .build();
    }
}
