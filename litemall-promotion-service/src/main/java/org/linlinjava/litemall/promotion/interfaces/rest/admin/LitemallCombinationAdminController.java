package org.linlinjava.litemall.promotion.interfaces.rest.admin;

import org.linlinjava.litemall.promotion.application.LitemallPromotionOrchestratorService;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCombinationAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCombinationPinkAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallActivateCombinationCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallDefineCombinationCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallExpireCombinationCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallUpdateCombinationCommand;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCombinationPinkStatus;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.linlinjava.litemall.promotion.interfaces.dtos.CombinationManagerDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.CombinationPinkDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.PromotionOperationDtoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

import static org.linlinjava.litemall.promotion.interfaces.util.LitemallHttpResponseUtil.buildResponse;

/**
 * Admin management of combination (group-buy) campaign definitions plus
 * activity monitoring over participation (pink). Under
 * {@code /srv/private/admin/**}, gated to {@code ROLE_ADMIN} by
 * litemall-svcsecurity.
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

    @PutMapping("/{combinationId}")
    public ResponseEntity<PromotionOperationDtoResponse> update(
            @PathVariable Integer combinationId,
            @RequestBody LitemallUpdateCombinationCommand command) {
        LitemallPromotionOperationResult result = orchestratorService.updateCombination(
                new LitemallCombinationId(combinationId), command);
        return buildResponse(result);
    }

    @DeleteMapping("/{combinationId}")
    public ResponseEntity<PromotionOperationDtoResponse> delete(@PathVariable Integer combinationId) {
        LitemallPromotionOperationResult result =
                orchestratorService.deleteCombination(new LitemallCombinationId(combinationId));
        return buildResponse(result);
    }

    /** Activity monitoring: groups of one campaign, optionally by status. */
    @GetMapping("/{combinationId}/pinks")
    public ResponseEntity<List<CombinationPinkDtoResponse>> listCampaignGroups(
            @PathVariable Integer combinationId,
            @RequestParam(required = false) Integer status) {
        return ResponseEntity.ok(listGroups(new LitemallCombinationId(combinationId), status));
    }

    /** Activity monitoring: groups across all campaigns, optionally by status. */
    @GetMapping("/pinks")
    public ResponseEntity<List<CombinationPinkDtoResponse>> listAllGroups(
            @RequestParam(required = false) Integer status) {
        return ResponseEntity.ok(listGroups(null, status));
    }

    private List<CombinationPinkDtoResponse> listGroups(LitemallCombinationId combinationId,
                                                        Integer status) {
        LitemallCombinationPinkStatus statusFilter =
                status != null ? LitemallCombinationPinkStatus.fromCode(status) : null;
        return orchestratorService.getCombinationService()
                .listGroups(combinationId, statusFilter).stream()
                .map(this::toPinkDto)
                .collect(Collectors.toList());
    }

    private CombinationPinkDtoResponse toPinkDto(LitemallCombinationPinkAggregate p) {
        int memberCount = orchestratorService.getCombinationService()
                .getGroup(p.getPinkId()).size();
        return CombinationPinkDtoResponse.builder()
                .pinkId(p.getPinkId() != null ? p.getPinkId().getId() : null)
                .combinationId(p.getCombinationId() != null ? p.getCombinationId().getId() : null)
                .userId(p.getUserId() != null ? p.getUserId().getId() : null)
                .orderId(p.getOrderId())
                .requiredMembers(p.getRequiredMembers())
                .memberCount(memberCount)
                .expireTime(p.getExpireTime())
                .status(p.getStatus() != null ? p.getStatus().getDisplayName() : null)
                .build();
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
