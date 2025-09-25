package org.linlinjava.litemall.order.domain.model.domainservices.groupon;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponRulesAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.user.LitemallUserAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRulesRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponRulesId;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.GrouponParticipationInfo;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

public class DomainGrouponService {

    private final LitemallGrouponRulesRepository grouponRulesRepository;
    private final LitemallGrouponRepository grouponRepository;

    public DomainGrouponService(LitemallGrouponRulesRepository grouponRulesRepository, LitemallGrouponRepository grouponRepository) {
        this.grouponRulesRepository = grouponRulesRepository;
        this.grouponRepository = grouponRepository;
    }

    //public GrouponValidationResult validateGrouponParticipation(GrouponRuleId ruleId, UserId userId) {
    public GrouponValidationResult validateGrouponParticipation(LitemallGrouponRulesAggregate rulesAggregate, LitemallUserAggregate userAggregate) {
        // The following code should be placed in GrouponServiceLayer
        /*GrouponRuleAggregate rule = grouponRuleRepository.findById(ruleId)
                .orElseThrow(() -> new GrouponRuleNotFoundException(ruleId));*/

        //if (!rulesAggregate.isActive()) {
        if (!rulesAggregate.isActive()) {
            return GrouponValidationResult.invalid("Groupon rule is not active");
        }

        // Check if user already participated in any active groupon for this rule
        /*boolean alreadyParticipated = grouponActivityRepository
                .findActiveGrouponsByUserAndRule(userId, ruleId)
                .stream()
                .anyMatch(activity -> activity.hasParticipant(userId));*/


        if (alreadyParticipated) {
            return GrouponValidationResult.invalid("User already participated in this groupon rule");
        }

        return GrouponValidationResult.valid();
    }

    public LitemallGrouponRulesAggregate validateAndGetGroupon(
            LitemallUserId userId,
            Integer grouponRulesId,
            Integer grouponLinkId
    ){
        // Validate basic input
        if (grouponRulesId == null || grouponRulesId <= 0) {
            return null;
        }

        // Get the aggregate from repository
        LitemallGrouponRulesAggregate grouponRules = grouponRulesRepository
                .findById(new LitemallGrouponRulesId(grouponRulesId));

        // Validate the aggregate
        grouponRules.validateGrouponRules();

        // Prepare participation info if needed
        if (grouponLinkId != null && grouponLinkId > 0) {
            GrouponParticipationInfo participationInfo = getParticipationInfo(userId, grouponLinkId);
            grouponRules.validateGrouponParticipation(userId, participationInfo);
        }

        return grouponRules;
    }

    // Not good because we interact with the repository in the domain service method
    private GrouponParticipationInfo getParticipationInfo(LitemallUserId userId, Integer grouponLinkId) {
        LitemallGrouponId linkId = new LitemallGrouponId(grouponLinkId);

        boolean grouponFull = grouponRepository.countByGrouponId(linkId) >=
                (grouponRules.getDiscountMember() - 1);

        boolean userAlreadyJoined = grouponRepository.existsByUserIdOrGrouponId(userId, linkId);

        LitemallGrouponAggregate groupon = grouponRepository.findById(linkId);
        boolean isCreator = groupon.getCreatorUserId().getId().equals(userId.getId());

        return GrouponParticipationInfo.create(true, grouponFull, userAlreadyJoined, isCreator);
    }
}
