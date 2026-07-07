package org.linlinjava.litemall.promotion.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallPromotionCampaignMapper;
import org.linlinjava.litemall.db.domain.LitemallPromotionCampaign;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallPromotionCampaignAggregate;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallPromotionCampaignRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCampaignId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LinkedPromotionType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCampaignStatus;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CampaignBudget;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerSegment;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.TargetingCriteria;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class LitemallPromotionCampaignRepositoryImpl implements LitemallPromotionCampaignRepository {

    private final LitemallPromotionCampaignMapper campaignMapper;

    public LitemallPromotionCampaignRepositoryImpl(LitemallPromotionCampaignMapper campaignMapper) {
        this.campaignMapper = campaignMapper;
    }

    @Override
    public Optional<LitemallPromotionCampaignAggregate> findById(LitemallCampaignId campaignId) {
        LitemallPromotionCampaign entity = campaignMapper.selectByPrimaryKey(campaignId.getId());
        return entity != null ? Optional.of(toDomain(entity)) : Optional.empty();
    }

    @Override
    public List<LitemallPromotionCampaignAggregate> findActive() {
        return campaignMapper.selectActive().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<LitemallPromotionCampaignAggregate> findAll() {
        return campaignMapper.selectAll().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public void save(LitemallPromotionCampaignAggregate campaign) {
        LitemallPromotionCampaign record = toData(campaign);
        if (campaign.getCampaignId() != null) {
            record.setUpdateTime(LocalDateTime.now());
            campaignMapper.updateByPrimaryKeySelective(record);
        } else {
            LocalDateTime now = LocalDateTime.now();
            record.setAddTime(now);
            record.setUpdateTime(now);
            record.setDeleted(false);
            campaignMapper.insertSelective(record);
            if (record.getId() != null) {
                campaign.setCampaignId(new LitemallCampaignId(record.getId()));
            }
        }
    }

    // ----- mapping -----

    private LitemallPromotionCampaignAggregate toDomain(LitemallPromotionCampaign r) {
        TargetingCriteria criteria = new TargetingCriteria(
                parseSegments(r.getTargetSegments()),
                r.getMinRecencyScore() != null ? (int) (short) r.getMinRecencyScore() : null,
                r.getMinFrequencyScore() != null ? (int) (short) r.getMinFrequencyScore() : null,
                r.getMinMonetaryScore() != null ? (int) (short) r.getMinMonetaryScore() : null);

        CampaignBudget budget = new CampaignBudget(
                r.getMaxAudience(),
                r.getMaxSpend() != null ? new LitemallMoney(r.getMaxSpend()) : null);

        return LitemallPromotionCampaignAggregate.builder()
                .campaignId(new LitemallCampaignId(r.getId()))
                .name(r.getName())
                .criteria(criteria)
                .targetGoodsIds(parseInts(r.getTargetGoodsIds()))
                .linkedPromotionType(LinkedPromotionType.fromName(r.getLinkedPromotionType()))
                .linkedPromotionId(r.getLinkedPromotionId())
                .startTime(r.getStartTime())
                .endTime(r.getEndTime())
                .budget(budget)
                .status(r.getStatus() != null ? LitemallCampaignStatus.fromCode(r.getStatus()) : LitemallCampaignStatus.DRAFT)
                .assignedCount(r.getAssignedCount() != null ? r.getAssignedCount() : 0)
                .spentBudget(r.getSpentBudget() != null ? new LitemallMoney(r.getSpentBudget()) : new LitemallMoney(BigDecimal.ZERO))
                .build();
    }

    private LitemallPromotionCampaign toData(LitemallPromotionCampaignAggregate agg) {
        LitemallPromotionCampaign r = new LitemallPromotionCampaign();
        if (agg.getCampaignId() != null) r.setId(agg.getCampaignId().getId());
        r.setName(agg.getName());

        TargetingCriteria criteria = agg.getCriteria();
        if (criteria != null) {
            r.setTargetSegments(joinSegments(criteria.getTargetSegments()));
            r.setMinRecencyScore(toShort(criteria.getMinRecencyScore()));
            r.setMinFrequencyScore(toShort(criteria.getMinFrequencyScore()));
            r.setMinMonetaryScore(toShort(criteria.getMinMonetaryScore()));
        }
        r.setTargetGoodsIds(joinInts(agg.getTargetGoodsIds()));
        r.setLinkedPromotionType(agg.getLinkedPromotionType() != null ? agg.getLinkedPromotionType().name() : LinkedPromotionType.NONE.name());
        r.setLinkedPromotionId(agg.getLinkedPromotionId());
        r.setStartTime(agg.getStartTime());
        r.setEndTime(agg.getEndTime());

        CampaignBudget budget = agg.getBudget();
        if (budget != null) {
            r.setMaxAudience(budget.getMaxAudience());
            r.setMaxSpend(budget.getMaxSpend() != null ? budget.getMaxSpend().getAmount() : null);
        }
        r.setAssignedCount(agg.getAssignedCount() != null ? agg.getAssignedCount() : 0);
        r.setSpentBudget(agg.getSpentBudget() != null ? agg.getSpentBudget().getAmount() : BigDecimal.ZERO);
        r.setStatus(agg.getStatus() != null ? (short) agg.getStatus().getCode() : (short) LitemallCampaignStatus.DRAFT.getCode());
        return r;
    }

    private Short toShort(Integer value) {
        return value != null ? value.shortValue() : null;
    }

    private Set<CustomerSegment> parseSegments(String csv) {
        if (csv == null || csv.isBlank()) {
            return EnumSet.noneOf(CustomerSegment.class);
        }
        Set<CustomerSegment> segments = EnumSet.noneOf(CustomerSegment.class);
        for (String token : csv.split(",")) {
            String t = token.trim();
            if (!t.isEmpty()) {
                segments.add(CustomerSegment.fromName(t));
            }
        }
        return segments;
    }

    private String joinSegments(Set<CustomerSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return "";
        }
        return segments.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private List<Integer> parseInts(String csv) {
        List<Integer> result = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return result;
        }
        for (String token : csv.split(",")) {
            String t = token.trim();
            if (!t.isEmpty()) {
                result.add(Integer.valueOf(t));
            }
        }
        return result;
    }

    private String joinInts(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
