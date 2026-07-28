package org.linlinjava.litemall.promotion.application.internal;

import org.linlinjava.litemall.promotion.application.internal.LitemallSocialPostServiceImpl.CampaignDraft;
import org.linlinjava.litemall.promotion.application.ports.SocialCatalogPort;
import org.linlinjava.litemall.promotion.application.ports.SocialCatalogPort.CategoryLiveDeals;
import org.linlinjava.litemall.promotion.application.ports.SocialCatalogPort.LiveDeal;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallPromotionCampaignAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.campaign.LitemallActivateCampaignCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.campaign.LitemallCampaignFromCategoryCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.campaign.LitemallDefineCampaignCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.campaign.LitemallEvaluateCampaignCommand;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCampaignId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LinkedPromotionType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCampaignStatus;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPlatform;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.linlinjava.litemall.promotion.infrastructure.configuration.PromotionCampaignProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Wave-12 scheduled category campaigns: the cross-vertical use-cases that
 * compose the Phase-2 campaign vertical with the Wave-6 social ledger.
 *
 * <p><b>{@link #scheduleTick()}</b> (driven by
 * {@code infrastructure/scheduling/CampaignScheduleTick}) executes campaign
 * schedules: DRAFT campaigns whose {@code start_time} has arrived are
 * activated + evaluated and their unpublished social drafts fired; ACTIVE
 * campaigns past {@code end_time} are completed. Only campaigns WITH a start
 * time are ever auto-activated — untimed drafts stay admin-triggered. All
 * state is DB state, so ticks are restart-safe and idempotent; each campaign
 * is guarded separately so one failure never aborts the sweep.
 *
 * <p><b>{@link #fromCategory}</b> is the category campaign composer behind
 * {@code POST /srv/private/admin/promotion/campaign/from-category}: target
 * goods = the category's live-deal goods (fallback: explicit goodsIds), one
 * campaign row linked to the deal mechanic, plus per-(goods × platform)
 * unpublished drafts correlated by the {@code utm_campaign=campaign-<id>}
 * slug. Publishing honest-degrades while platform adapters are disabled.
 */
@Service
public class LitemallCampaignSchedulingServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(LitemallCampaignSchedulingServiceImpl.class);

    private final LitemallCampaignServiceImpl campaignService;
    private final LitemallSocialPostServiceImpl socialPostService;
    private final SocialCatalogPort socialCatalogPort;
    private final PromotionCampaignProperties properties;

    public LitemallCampaignSchedulingServiceImpl(LitemallCampaignServiceImpl campaignService,
                                                 LitemallSocialPostServiceImpl socialPostService,
                                                 SocialCatalogPort socialCatalogPort,
                                                 PromotionCampaignProperties properties) {
        this.campaignService = campaignService;
        this.socialPostService = socialPostService;
        this.socialCatalogPort = socialCatalogPort;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Schedule tick
    // ------------------------------------------------------------------

    public void scheduleTick() {
        if (!properties.isSchedulerEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int deadWindows = 0;
        for (LitemallPromotionCampaignAggregate campaign : campaignService.listCampaigns()) {
            try {
                if (campaign.getStatus() == LitemallCampaignStatus.DRAFT
                        && campaign.getStartTime() != null
                        && !now.isBefore(campaign.getStartTime())) {
                    if (campaign.isExpired(now)) {
                        // Whole window slept through (service down / created in the
                        // past): activating would complete immediately — leave the
                        // draft for the admin instead of ghost-running it.
                        deadWindows++;
                        continue;
                    }
                    activateDueCampaign(campaign.getCampaignId());
                } else if (campaign.isActive() && campaign.isExpired(now)) {
                    LitemallPromotionOperationResult completed =
                            campaignService.completeCampaign(campaign.getCampaignId());
                    logger.info("campaign schedule: {} past end — complete: {}",
                            campaign.getCampaignId().getId(), completed.getMessage());
                }
            } catch (Exception e) {
                logger.error("campaign schedule: campaign {} tick step failed",
                        campaign.getCampaignId() != null ? campaign.getCampaignId().getId() : null, e);
            }
        }
        if (deadWindows > 0) {
            logger.info("campaign schedule: {} DRAFT campaign(s) whose window already ended were left untouched",
                    deadWindows);
        }
    }

    /** Activate, then evaluate, then fire drafts — each guarded so a later step's failure never undoes an earlier one. */
    private void activateDueCampaign(LitemallCampaignId campaignId) {
        LitemallPromotionOperationResult activated =
                campaignService.activateCampaign(new LitemallActivateCampaignCommand(campaignId));
        if (!activated.isSuccess()) {
            logger.warn("campaign schedule: {} due but activation failed: {}",
                    campaignId.getId(), activated.getMessage());
            return;
        }
        logger.info("campaign schedule: {} activated on schedule", campaignId.getId());
        try {
            LitemallPromotionOperationResult evaluated =
                    campaignService.evaluateCampaign(new LitemallEvaluateCampaignCommand(campaignId));
            logger.info("campaign schedule: {} evaluated — audienceSize={}",
                    campaignId.getId(), evaluated.getData().get("audienceSize"));
        } catch (Exception e) {
            // Audience delivery can lag (stats source down); social posting still goes out.
            logger.error("campaign schedule: {} activated but evaluation failed", campaignId.getId(), e);
        }
        socialPostService.publishCampaignDrafts(campaignId.getId());
    }

    // ------------------------------------------------------------------
    // Category campaign composer
    // ------------------------------------------------------------------

    public LitemallPromotionOperationResult fromCategory(LitemallCampaignFromCategoryCommand command,
                                                         String postedBy) {
        if (command.getCategoryL1Id() == null) {
            return LitemallPromotionOperationResult.campaignFromCategoryFailed("categoryL1Id is required");
        }
        LitemallCampaignFromCategoryCommand.Schedule schedule = command.getSchedule();
        if (schedule == null || schedule.getStart() == null || schedule.getStop() == null) {
            return LitemallPromotionOperationResult.campaignFromCategoryFailed(
                    "schedule.start and schedule.stop are required");
        }
        if (!schedule.getStart().isBefore(schedule.getStop())) {
            return LitemallPromotionOperationResult.campaignFromCategoryFailed(
                    "schedule.start must be before schedule.stop");
        }
        if (schedule.getStop().isBefore(LocalDateTime.now())) {
            return LitemallPromotionOperationResult.campaignFromCategoryFailed(
                    "schedule.stop is already in the past");
        }
        List<LitemallSocialPlatform> platforms = new ArrayList<>();
        if (command.getPlatforms() != null) {
            for (String dbValue : new LinkedHashSet<>(command.getPlatforms())) {
                LitemallSocialPlatform platform = LitemallSocialPlatform.fromDbValue(dbValue);
                if (platform == null) {
                    return LitemallPromotionOperationResult.campaignFromCategoryFailed(
                            "unknown platform '" + dbValue + "' — expected meta_fb|meta_ig|tiktok");
                }
                platforms.add(platform);
            }
        }
        if (platforms.isEmpty()) {
            return LitemallPromotionOperationResult.campaignFromCategoryFailed(
                    "platforms is required (meta_fb|meta_ig|tiktok)");
        }

        CategoryLiveDeals category = socialCatalogPort.categoryLiveDeals(command.getCategoryL1Id()).orElse(null);
        if (category == null) {
            return LitemallPromotionOperationResult.campaignFromCategoryFailed(
                    "category " + command.getCategoryL1Id() + " not found");
        }

        List<Integer> targetGoodsIds = new ArrayList<>(new LinkedHashSet<>(
                category.deals().stream().map(LiveDeal::goodsId).toList()));
        LinkedPromotionType linkedType = LinkedPromotionType.SECKILL;
        Integer linkedPromotionId = category.deals().isEmpty() ? null : category.deals().get(0).dealId();
        if (targetGoodsIds.isEmpty()) {
            if (command.getGoodsIds() == null || command.getGoodsIds().isEmpty()) {
                return LitemallPromotionOperationResult.campaignFromCategoryFailed(
                        "category '" + category.categoryName()
                                + "' has no live-deal goods and no explicit goodsIds were given");
            }
            targetGoodsIds = new ArrayList<>(new LinkedHashSet<>(command.getGoodsIds()));
            linkedType = LinkedPromotionType.NONE;
        }

        String name = StringUtils.hasText(command.getName())
                ? command.getName()
                : category.categoryName() + " campaign";

        LitemallPromotionOperationResult defined = campaignService.defineCampaign(
                LitemallDefineCampaignCommand.builder()
                        .name(name)
                        .targetGoodsIds(targetGoodsIds)
                        .linkedPromotionType(linkedType.name())
                        .linkedPromotionId(linkedPromotionId)
                        .startTime(schedule.getStart())
                        .endTime(schedule.getStop())
                        .build());
        if (!defined.isSuccess()) {
            return defined;
        }
        Integer campaignId = (Integer) defined.getData().get("campaignId");

        List<CampaignDraft> drafts =
                socialPostService.createCampaignDrafts(campaignId, targetGoodsIds, platforms, postedBy);

        Map<String, Object> data = new HashMap<>();
        data.put("campaignId", campaignId);
        data.put("name", name);
        data.put("targetGoodsIds", targetGoodsIds);
        data.put("postIds", drafts.stream().map(CampaignDraft::postId).toList());
        data.put("platforms", platformStatuses(platforms, drafts));
        return LitemallPromotionOperationResult.campaignComposedFromCategory(data);
    }

    /** Honest per-platform outlook: drafts are unpublished, so status reports adapter enablement, not outcomes. */
    private List<Map<String, Object>> platformStatuses(List<LitemallSocialPlatform> platforms,
                                                       List<CampaignDraft> drafts) {
        List<Map<String, Object>> statuses = new ArrayList<>();
        for (LitemallSocialPlatform platform : platforms) {
            boolean enabled = socialPostService.isPlatformEnabled(platform);
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("platform", platform.getDbValue());
            status.put("enabled", enabled);
            status.put("draftCount", drafts.stream().filter(d -> d.platform() == platform).count());
            status.put("status", enabled ? "scheduled" : "disabled");
            if (!enabled) {
                status.put("reason",
                        "adapter disabled — drafts will record as failed when the campaign activates");
            }
            statuses.add(status);
        }
        return statuses;
    }
}
