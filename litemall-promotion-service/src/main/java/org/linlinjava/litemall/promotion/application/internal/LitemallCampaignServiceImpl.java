package org.linlinjava.litemall.promotion.application.internal;

import org.linlinjava.litemall.promotion.application.ports.CrawledMarketDataProvider;
import org.linlinjava.litemall.promotion.application.ports.CustomerStatisticsProvider;
import org.linlinjava.litemall.promotion.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.promotion.domain.events.campaign.LitemallCampaignActivatedEvent;
import org.linlinjava.litemall.promotion.domain.events.campaign.LitemallCampaignDefinedEvent;
import org.linlinjava.litemall.promotion.domain.events.campaign.PromotionTargetedEvent;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallPromotionCampaignAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.campaign.LitemallActivateCampaignCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.campaign.LitemallDefineCampaignCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.campaign.LitemallEvaluateCampaignCommand;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallPromotionCampaignRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCampaignId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LinkedPromotionType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCampaignStatus;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.AudienceMember;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CampaignBudget;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerSegment;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerStatistics;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.TargetingCriteria;
import org.linlinjava.litemall.promotion.domain.service.LitemallCampaignTargetingDomainService;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.linlinjava.litemall.promotion.infrastructure.configuration.PromotionTargetingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Application service for the Phase-2 algorithmic-targeting campaign vertical.
 * Mirrors {@code LitemallCombinationServiceImpl}: admin defines a campaign
 * (DRAFT) and activates it; evaluation computes an audience from real customer
 * statistics (behind the {@link CustomerStatisticsProvider} port) via the
 * targeting domain service and emits a {@link PromotionTargetedEvent}.
 */
@Service
@Transactional
public class LitemallCampaignServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(LitemallCampaignServiceImpl.class);

    private final LitemallPromotionCampaignRepository campaignRepository;
    private final LitemallCampaignTargetingDomainService targetingDomainService;
    private final CustomerStatisticsProvider statisticsProvider;
    private final CrawledMarketDataProvider crawledMarketDataProvider;
    private final PromotionTargetingProperties targetingProperties;
    private final LitemallDomainEventPublisher domainEventPublisher;

    public LitemallCampaignServiceImpl(LitemallPromotionCampaignRepository campaignRepository,
                                       LitemallCampaignTargetingDomainService targetingDomainService,
                                       CustomerStatisticsProvider statisticsProvider,
                                       CrawledMarketDataProvider crawledMarketDataProvider,
                                       PromotionTargetingProperties targetingProperties,
                                       LitemallDomainEventPublisher domainEventPublisher) {
        this.campaignRepository = campaignRepository;
        this.targetingDomainService = targetingDomainService;
        this.statisticsProvider = statisticsProvider;
        this.crawledMarketDataProvider = crawledMarketDataProvider;
        this.targetingProperties = targetingProperties;
        this.domainEventPublisher = domainEventPublisher;
    }

    /** Admin: define a campaign (created DRAFT). */
    public LitemallPromotionOperationResult defineCampaign(LitemallDefineCampaignCommand command) {
        logger.info("Defining campaign: name={}, segments={}", command.getName(), command.getTargetSegments());

        if (command.getName() == null || command.getName().isBlank()) {
            throw new IllegalArgumentException("Campaign name is required");
        }

        TargetingCriteria criteria = new TargetingCriteria(
                parseSegments(command.getTargetSegments()),
                command.getMinRecencyScore(),
                command.getMinFrequencyScore(),
                command.getMinMonetaryScore());

        CampaignBudget budget = new CampaignBudget(
                command.getMaxAudience(),
                command.getMaxSpend() != null ? new LitemallMoney(command.getMaxSpend()) : null);

        LitemallPromotionCampaignAggregate campaign = LitemallPromotionCampaignAggregate.builder()
                .name(command.getName())
                .criteria(criteria)
                .targetGoodsIds(command.getTargetGoodsIds() != null ? command.getTargetGoodsIds() : new java.util.ArrayList<>())
                .linkedPromotionType(LinkedPromotionType.fromName(command.getLinkedPromotionType()))
                .linkedPromotionId(command.getLinkedPromotionId())
                .startTime(command.getStartTime())
                .endTime(command.getEndTime())
                .budget(budget)
                .status(LitemallCampaignStatus.DRAFT)
                .build();

        campaignRepository.save(campaign);

        domainEventPublisher.publish(new LitemallCampaignDefinedEvent(campaign.getCampaignId(), campaign.getName()));

        Map<String, Object> data = new HashMap<>();
        data.put("campaignId", campaign.getCampaignId().getId());
        return LitemallPromotionOperationResult.campaignDefined(data);
    }

    /** Admin: make a DRAFT/PAUSED campaign ACTIVE. */
    public LitemallPromotionOperationResult activateCampaign(LitemallActivateCampaignCommand command) {
        Optional<LitemallPromotionCampaignAggregate> opt = campaignRepository.findById(command.getCampaignId());
        if (opt.isEmpty()) {
            return LitemallPromotionOperationResult.campaignStateChangeFailed("Campaign not found");
        }
        LitemallPromotionCampaignAggregate campaign = opt.get();
        campaign.activate();
        campaignRepository.save(campaign);

        domainEventPublisher.publish(new LitemallCampaignActivatedEvent(campaign.getCampaignId()));

        Map<String, Object> data = new HashMap<>();
        data.put("campaignId", campaign.getCampaignId().getId());
        return LitemallPromotionOperationResult.campaignActivated(data);
    }

    /**
     * Admin: evaluate a campaign — compute its target audience from real
     * customer statistics and produce a promotion assignment (returned + emitted
     * as {@link PromotionTargetedEvent}).
     */
    public LitemallPromotionOperationResult evaluateCampaign(LitemallEvaluateCampaignCommand command) {
        Optional<LitemallPromotionCampaignAggregate> opt = campaignRepository.findById(command.getCampaignId());
        if (opt.isEmpty()) {
            return LitemallPromotionOperationResult.campaignEvaluateFailed("Campaign not found");
        }
        LitemallPromotionCampaignAggregate campaign = opt.get();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusDays(targetingProperties.getStatsLookbackDays());
        List<CustomerStatistics> population = statisticsProvider.fetchSince(since);
        logger.info("Evaluating campaign {}: {} customers in population (since {})",
                campaign.getCampaignId().getId(), population.size(), since);

        // Optional Phase-3 enrichment: crawled market signals (Nutch → read model).
        // Best-effort and config-gated — returns empty when disabled/unreachable, so
        // evaluation is unaffected; the count is surfaced for observability.
        int crawledSignalCount = crawledMarketDataProvider.fetchSignals().size();
        if (crawledSignalCount > 0) {
            logger.info("Campaign {}: {} crawled market signals available for enrichment",
                    campaign.getCampaignId().getId(), crawledSignalCount);
        }

        List<AudienceMember> audience = targetingDomainService.selectAudience(campaign, population, now);
        campaignRepository.save(campaign);

        List<Integer> audienceUserIds = audience.stream()
                .map(m -> m.getUserId().getId())
                .collect(Collectors.toList());

        domainEventPublisher.publish(new PromotionTargetedEvent(
                campaign.getCampaignId(),
                audienceUserIds,
                campaign.getLinkedPromotionType() != null ? campaign.getLinkedPromotionType().name() : LinkedPromotionType.NONE.name(),
                campaign.getLinkedPromotionId()));

        Map<String, Object> data = new HashMap<>();
        data.put("campaignId", campaign.getCampaignId().getId());
        data.put("audienceSize", audienceUserIds.size());
        data.put("audienceUserIds", audienceUserIds);
        data.put("segmentBreakdown", segmentBreakdown(audience));
        data.put("crawledSignalCount", crawledSignalCount);
        return LitemallPromotionOperationResult.campaignEvaluated(data);
    }

    /**
     * Scheduler (Wave-12): mark a past-end ACTIVE campaign COMPLETED. No
     * domain event exists for completion — the terminal state itself is the
     * signal (mirrors the group-buy expiry sweep).
     */
    public LitemallPromotionOperationResult completeCampaign(LitemallCampaignId campaignId) {
        Optional<LitemallPromotionCampaignAggregate> opt = campaignRepository.findById(campaignId);
        if (opt.isEmpty()) {
            return LitemallPromotionOperationResult.campaignCompleteFailed("Campaign not found");
        }
        LitemallPromotionCampaignAggregate campaign = opt.get();
        campaign.complete();
        campaignRepository.save(campaign);

        Map<String, Object> data = new HashMap<>();
        data.put("campaignId", campaign.getCampaignId().getId());
        return LitemallPromotionOperationResult.campaignCompleted(data);
    }

    // ----- read models -----

    @Transactional(readOnly = true)
    public List<LitemallPromotionCampaignAggregate> listCampaigns() {
        return campaignRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<LitemallPromotionCampaignAggregate> getCampaign(LitemallCampaignId campaignId) {
        return campaignRepository.findById(campaignId);
    }

    // ----- helpers -----

    private Set<CustomerSegment> parseSegments(List<String> names) {
        Set<CustomerSegment> segments = EnumSet.noneOf(CustomerSegment.class);
        if (names != null) {
            for (String name : names) {
                if (name != null && !name.isBlank()) {
                    segments.add(CustomerSegment.fromName(name));
                }
            }
        }
        return segments;
    }

    private Map<String, Long> segmentBreakdown(List<AudienceMember> audience) {
        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (AudienceMember member : audience) {
            breakdown.merge(member.getSegment().name(), 1L, Long::sum);
        }
        return breakdown;
    }
}
