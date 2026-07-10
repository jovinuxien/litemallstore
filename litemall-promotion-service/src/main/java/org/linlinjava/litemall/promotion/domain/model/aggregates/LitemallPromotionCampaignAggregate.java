package org.linlinjava.litemall.promotion.domain.model.aggregates;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCampaignId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LinkedPromotionType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCampaignStatus;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CampaignBudget;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.TargetingCriteria;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Algorithmic-targeting promotion campaign aggregate (Phase 2). Carries the
 * targeting {@link TargetingCriteria}, the target goods + a linked Phase-1 promo
 * mechanic ({@link LinkedPromotionType} + id, reused not duplicated), a
 * schedule, and a {@link CampaignBudget} (Katsov §3.6.2). The targeting engine
 * evaluates the criteria against real customer statistics, selects an audience
 * (capped by the budget), and records the assignment. Backed by litemall-db
 * {@code litemall_promotion_campaign}.
 */
@Getter
@Setter
@Builder
public class LitemallPromotionCampaignAggregate {

    private LitemallCampaignId campaignId;
    private String name;
    private TargetingCriteria criteria;
    @Builder.Default
    private List<Integer> targetGoodsIds = new ArrayList<>();
    @Builder.Default
    private LinkedPromotionType linkedPromotionType = LinkedPromotionType.NONE;
    private Integer linkedPromotionId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @Builder.Default
    private CampaignBudget budget = CampaignBudget.uncapped();
    @Builder.Default
    private LitemallCampaignStatus status = LitemallCampaignStatus.DRAFT;
    @Builder.Default
    private Integer assignedCount = 0;
    @Builder.Default
    private LitemallMoney spentBudget = new LitemallMoney(BigDecimal.ZERO);

    @Builder.Default
    private List<LitemallDomainEvent> domainEvents = new ArrayList<>();

    public boolean isActive() {
        return LitemallCampaignStatus.ACTIVE.equals(this.status);
    }

    public boolean isExpired(LocalDateTime now) {
        return this.endTime != null && now.isAfter(this.endTime);
    }

    /** Whether the campaign may currently be evaluated to deliver assignments. */
    public boolean canEvaluate(LocalDateTime now) {
        boolean started = startTime == null || !now.isBefore(startTime);
        return isActive() && started && !isExpired(now);
    }

    /** Promote a DRAFT/PAUSED campaign to ACTIVE so its assignments deliver. */
    public void activate() {
        if (this.status == LitemallCampaignStatus.COMPLETED) {
            throw new IllegalStateException("A completed campaign cannot be re-activated");
        }
        this.status = LitemallCampaignStatus.ACTIVE;
    }

    public void pause() {
        this.status = LitemallCampaignStatus.PAUSED;
    }

    public void complete() {
        this.status = LitemallCampaignStatus.COMPLETED;
    }

    /**
     * Record the audience size produced by an evaluation. The targeting domain
     * service is responsible for capping the audience to the budget before
     * calling this.
     */
    public void recordAssignment(int audienceSize) {
        this.assignedCount = audienceSize;
    }

    public void addDomainEvent(LitemallDomainEvent event) {
        this.domainEvents.add(event);
    }

    public void clearDomainEvents() {
        this.domainEvents.clear();
    }

    public List<LitemallDomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(this.domainEvents);
    }
}
