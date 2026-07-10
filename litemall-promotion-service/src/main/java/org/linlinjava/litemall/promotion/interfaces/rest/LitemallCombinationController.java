package org.linlinjava.litemall.promotion.interfaces.rest;

import org.linlinjava.litemall.promotion.application.LitemallPromotionOrchestratorService;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCombinationAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCombinationPinkAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallJoinGroupCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallStartGroupCommand;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationPinkId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.linlinjava.litemall.promotion.interfaces.dtos.CombinationDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.CombinationPinkDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.PromotionOperationDtoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

import static org.linlinjava.litemall.promotion.interfaces.util.LitemallHttpResponseUtil.buildResponse;

/**
 * Customer-facing combination (group-buy) surface: browse active campaigns and
 * participate — start a group, join an open group, list my groups. Promotion
 * owns participation since Wave 2 (see the updated ADR).
 */
@RestController
@RequestMapping("/srv/promotion/combination")
public class LitemallCombinationController {

    private final LitemallPromotionOrchestratorService orchestratorService;

    public LitemallCombinationController(LitemallPromotionOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    /** Start a new group on an active campaign; the caller becomes its leader. */
    @PostMapping("/{combinationId}/start")
    public ResponseEntity<PromotionOperationDtoResponse> startGroup(
            @PathVariable Integer combinationId,
            @RequestHeader("X-User-Id") Integer userId) {
        LitemallStartGroupCommand command = new LitemallStartGroupCommand(
                new LitemallUserId(userId), new LitemallCombinationId(combinationId));
        LitemallPromotionOperationResult result = orchestratorService.startGroup(command);
        return buildResponse(result);
    }

    /** Join an open group by its leader slot ("pink") id. */
    @PostMapping("/pink/{pinkId}/join")
    public ResponseEntity<PromotionOperationDtoResponse> joinGroup(
            @PathVariable Integer pinkId,
            @RequestHeader("X-User-Id") Integer userId) {
        LitemallJoinGroupCommand command = new LitemallJoinGroupCommand(
                new LitemallUserId(userId), new LitemallCombinationPinkId(pinkId));
        LitemallPromotionOperationResult result = orchestratorService.joinGroup(command);
        return buildResponse(result);
    }

    /** My participation slots, newest first. */
    @GetMapping("/my")
    public ResponseEntity<List<CombinationPinkDtoResponse>> getMyGroups(
            @RequestHeader("X-User-Id") Integer userId) {
        List<CombinationPinkDtoResponse> response = orchestratorService.getCombinationService()
                .getMyGroups(new LitemallUserId(userId)).stream()
                .map(p -> toPinkDto(p, null, null))
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /** A group's state: leader slot with member count and member slots. */
    @GetMapping("/pink/{pinkId}")
    public ResponseEntity<CombinationPinkDtoResponse> getGroup(@PathVariable Integer pinkId) {
        LitemallCombinationPinkId id = new LitemallCombinationPinkId(pinkId);
        return orchestratorService.getCombinationService().getPink(id)
                .map(slot -> {
                    LitemallCombinationPinkId headId = slot.isLeader() ? slot.getPinkId() : slot.getHeadId();
                    List<LitemallCombinationPinkAggregate> slots =
                            orchestratorService.getCombinationService().getGroup(headId);
                    LitemallCombinationPinkAggregate leader = slots.stream()
                            .filter(LitemallCombinationPinkAggregate::isLeader)
                            .findFirst().orElse(slot);
                    List<CombinationPinkDtoResponse> members = slots.stream()
                            .filter(s -> !s.isLeader())
                            .map(s -> toPinkDto(s, null, null))
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(toPinkDto(leader, slots.size(), members));
                })
                .orElse(ResponseEntity.notFound().build());
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

    private CombinationPinkDtoResponse toPinkDto(LitemallCombinationPinkAggregate p,
                                                 Integer memberCount,
                                                 List<CombinationPinkDtoResponse> members) {
        return CombinationPinkDtoResponse.builder()
                .pinkId(p.getPinkId() != null ? p.getPinkId().getId() : null)
                .combinationId(p.getCombinationId() != null ? p.getCombinationId().getId() : null)
                .headId(p.getHeadId() != null ? p.getHeadId().getId() : null)
                .userId(p.getUserId() != null ? p.getUserId().getId() : null)
                .orderId(p.getOrderId())
                .requiredMembers(p.getRequiredMembers())
                .memberCount(memberCount)
                .expireTime(p.getExpireTime())
                .status(p.getStatus() != null ? p.getStatus().getDisplayName() : null)
                .members(members)
                .build();
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
