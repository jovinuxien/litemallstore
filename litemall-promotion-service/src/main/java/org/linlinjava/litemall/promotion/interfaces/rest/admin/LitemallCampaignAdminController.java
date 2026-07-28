package org.linlinjava.litemall.promotion.interfaces.rest.admin;

import org.linlinjava.litemall.promotion.application.LitemallPromotionOrchestratorService;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallPromotionCampaignAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.campaign.LitemallActivateCampaignCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.campaign.LitemallCampaignFromCategoryCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.campaign.LitemallDefineCampaignCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.campaign.LitemallEvaluateCampaignCommand;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCampaignId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerSegment;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.linlinjava.litemall.promotion.interfaces.dtos.CampaignManagerDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.PromotionOperationDtoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

import static org.linlinjava.litemall.promotion.interfaces.util.LitemallHttpResponseUtil.buildResponse;

/**
 * Admin management of algorithmic-targeting campaigns (Phase 2). Under
 * {@code /srv/private/admin/**}, gated to {@code ROLE_ADMIN} by
 * litemall-svcsecurity — the customer surface carries no campaign endpoints.
 * Evaluation computes the target audience from real customer statistics and
 * returns/emits the assignment.
 */
@RestController
@RequestMapping("/srv/private/admin/promotion/campaign")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
public class LitemallCampaignAdminController {

    private final LitemallPromotionOrchestratorService orchestratorService;

    public LitemallCampaignAdminController(LitemallPromotionOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @GetMapping("/list")
    public ResponseEntity<List<CampaignManagerDtoResponse>> list() {
        List<CampaignManagerDtoResponse> response = orchestratorService.getCampaignService()
                .listCampaigns().stream()
                .map(this::toManagerDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignManagerDtoResponse> get(@PathVariable Integer campaignId) {
        return orchestratorService.getCampaignService()
                .getCampaign(new LitemallCampaignId(campaignId))
                .map(c -> ResponseEntity.ok(toManagerDto(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PromotionOperationDtoResponse> define(
            @RequestBody LitemallDefineCampaignCommand command) {
        LitemallPromotionOperationResult result = orchestratorService.defineCampaign(command);
        return buildResponse(result);
    }

    @PostMapping("/{campaignId}/activate")
    public ResponseEntity<PromotionOperationDtoResponse> activate(@PathVariable Integer campaignId) {
        LitemallPromotionOperationResult result = orchestratorService.activateCampaign(
                new LitemallActivateCampaignCommand(new LitemallCampaignId(campaignId)));
        return buildResponse(result);
    }

    /**
     * Wave-12 category campaign composer: one call = scheduled campaign row +
     * unpublished per-platform social drafts; the schedule tick activates and
     * fires them at {@code schedule.start}. Per-platform statuses in the
     * response are honest about disabled adapters.
     */
    @PostMapping("/from-category")
    public ResponseEntity<PromotionOperationDtoResponse> fromCategory(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestBody LitemallCampaignFromCategoryCommand command) {
        LitemallPromotionOperationResult result = orchestratorService.campaignFromCategory(
                command, StringUtils.hasText(adminId) ? adminId : "admin");
        return buildResponse(result);
    }

    @PostMapping("/{campaignId}/evaluate")
    public ResponseEntity<PromotionOperationDtoResponse> evaluate(@PathVariable Integer campaignId) {
        LitemallPromotionOperationResult result = orchestratorService.evaluateCampaign(
                new LitemallEvaluateCampaignCommand(new LitemallCampaignId(campaignId)));
        return buildResponse(result);
    }

    private CampaignManagerDtoResponse toManagerDto(LitemallPromotionCampaignAggregate c) {
        return CampaignManagerDtoResponse.builder()
                .campaignId(c.getCampaignId() != null ? c.getCampaignId().getId() : null)
                .name(c.getName())
                .targetSegments(c.getCriteria() != null
                        ? c.getCriteria().getTargetSegments().stream().map(CustomerSegment::name).collect(Collectors.toList())
                        : null)
                .minRecencyScore(c.getCriteria() != null ? c.getCriteria().getMinRecencyScore() : null)
                .minFrequencyScore(c.getCriteria() != null ? c.getCriteria().getMinFrequencyScore() : null)
                .minMonetaryScore(c.getCriteria() != null ? c.getCriteria().getMinMonetaryScore() : null)
                .targetGoodsIds(c.getTargetGoodsIds())
                .linkedPromotionType(c.getLinkedPromotionType() != null ? c.getLinkedPromotionType().name() : null)
                .linkedPromotionId(c.getLinkedPromotionId())
                .startTime(c.getStartTime())
                .endTime(c.getEndTime())
                .maxAudience(c.getBudget() != null ? c.getBudget().getMaxAudience() : null)
                .maxSpend(c.getBudget() != null && c.getBudget().getMaxSpend() != null
                        ? c.getBudget().getMaxSpend().getAmount() : null)
                .assignedCount(c.getAssignedCount())
                .spentBudget(c.getSpentBudget() != null ? c.getSpentBudget().getAmount() : null)
                .status(c.getStatus() != null ? c.getStatus().getDisplayName() : null)
                .build();
    }
}
