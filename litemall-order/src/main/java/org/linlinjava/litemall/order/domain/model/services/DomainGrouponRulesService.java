package org.linlinjava.litemall.order.domain.model.services;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponRulesAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRulesRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponRulesId;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.GrouponParticipationInfo;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

public class DomainGrouponRulesService {

    private final LitemallGrouponRulesRepository grouponRulesRepository;
    private final LitemallGrouponRepository grouponRepository;

    public DomainGrouponRulesService(LitemallGrouponRulesRepository grouponRulesRepository, LitemallGrouponRepository grouponRepository) {
        this.grouponRulesRepository = grouponRulesRepository;
        this.grouponRepository = grouponRepository;
    }

    public LitemallGrouponRulesAggregate validateAndGetGroupon(LitemallUserId userId, Integer grouponRulesId, Integer grouponLinkId){

        //Validate basic input
        if(grouponRulesId == null || grouponRulesId <= 0){
            return null;
        }

        // Get the aggregate from repository
        LitemallGrouponRulesAggregate grouponRules = grouponRulesRepository.findById(new LitemallGrouponRulesId(grouponRulesId));
        // Validate the aggregate
        grouponRules.validateGrouponRules();

        // Prepare participation information
        if (grouponLinkId != null && grouponLinkId > 0) {
            GrouponParticipationInfo participationInfo = getParticipationInfo(userId, grouponLinkId, grouponRules);

            grouponRules.validateGrouponParticipation(userId, participationInfo);
        }

        return grouponRules;

    }

    private GrouponParticipationInfo getParticipationInfo(LitemallUserId userId, Integer grouponLinkId, LitemallGrouponRulesAggregate grouponRules) {

        LitemallGrouponId linkId = new LitemallGrouponId(grouponLinkId);

        boolean grouponFull = grouponRepository.countByGrouponId(linkId) >= (grouponRules.getDiscountMember() - 1);

        boolean userAlreadyJoined = grouponRepository.existsByUserIdOrGrouponId(userId, linkId);

        LitemallGrouponAggregate groupon = grouponRepository.findById(linkId);
        boolean isCreator = groupon.getCreatorUserId().getId().equals(userId.getId());

        return GrouponParticipationInfo.create(true, grouponFull, userAlreadyJoined, isCreator);
    }
}
