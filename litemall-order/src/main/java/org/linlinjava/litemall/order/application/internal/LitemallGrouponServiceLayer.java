package org.linlinjava.litemall.order.application.internal;


import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponRulesAggregate;
import org.linlinjava.litemall.order.domain.model.domainservices.groupon.LitemallGrouponValidationResult;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRulesRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponRulesId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallGrouponStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class LitemallGrouponServiceLayer {

    private final LitemallGrouponRulesRepository grouponRulesRepository;
    private final LitemallGrouponRepository grouponRepository;

    public LitemallGrouponServiceLayer(LitemallGrouponRulesRepository grouponRulesRepository, LitemallGrouponRepository grouponRepository) {
        this.grouponRulesRepository = grouponRulesRepository;
        this.grouponRepository = grouponRepository;
    }


    /**
     * @Description: Validate groupon rules
     * @param grouponRulesId
     * @param grouponLinkId
     * @param userId
     * @return
     */
    public LitemallGrouponValidationResult validateGrouponRules(Integer grouponRulesId, Integer grouponLinkId, Integer userId) {

        // Get the aggregate from repository
        LitemallGrouponRulesAggregate grouponRules = grouponRulesRepository
                .findById(new LitemallGrouponRulesId(grouponRulesId));
        if(grouponRules == null){
            throw new IllegalArgumentException("Groupon rules not found.");
        }

        if(grouponRules.getStatus().equals(LitemallGrouponStatus.RULE_STATUS_DOWN_EXPIRE)){
            return LitemallGrouponValidationResult.grouponDownExpired();
        }

        if(grouponRules.getStatus().equals(LitemallGrouponStatus.RULE_STATUS_DOWN_ADMIN)){
            return LitemallGrouponValidationResult.grouponOffline();
        }

        if(grouponLinkId > 0){
            LitemallGrouponId grouponId = new LitemallGrouponId(grouponLinkId);

            //Group purchase is full
            if(grouponRepository.countGroupon(grouponId) >= (grouponRules.getDiscountMember() -1)){
                return LitemallGrouponValidationResult.grouponFull();
            }
            // NOTE
            // The business aspect here allows users to start groups multiple times，and participated in many tours，
            // But it will limit the following two points：
            // （1）Not allowed to participate in group purchases that have already been joined
            if(grouponRepository.hasJoin(new LitemallUserId(userId), grouponId)){
                return LitemallGrouponValidationResult.invalidAlreadyJoined();
            }
            //（2）Not allowed to participate in group buying organized by oneself
            LitemallUserId userIdentity = new LitemallUserId(userId);
            LitemallGrouponAggregate grouponAggregate = grouponRepository.findByUserId(grouponId, userIdentity);

            if(grouponAggregate != null) {
                if(grouponAggregate.getCreatorUserId().equals(userId)){
                    return LitemallGrouponValidationResult.invalidAlreadyJoined();
                }
            }
            return LitemallGrouponValidationResult.valid();
        }
        return LitemallGrouponValidationResult.valid();
    }

    public LitemallMoney getGrouponDiscount(LitemallGrouponRulesId grouponRulesId){
        LitemallGrouponRulesAggregate rulesAggregate = grouponRulesRepository.findById(grouponRulesId);

        if(rulesAggregate == null){
            throw  new  IllegalArgumentException("Groupon rules not found");
        }
        return new LitemallMoney(rulesAggregate.getDiscount());
    }


    /**
     * @Description: Vérifier si l'utilisateur peut participer à un groupe donné'
     * @param userId
     * @param rulesId
     * @return
     */
    public boolean canUserParticipate(LitemallUserId userId, LitemallGrouponRulesId rulesId) {
        int participationCount = grouponRepository.countUserActiveParticipationsInRules(userId, rulesId);
        return participationCount == 0;
    }



    private LitemallGrouponValidationResult validateUserParticipation(LitemallUserId userId, LitemallGrouponRulesId rulesId, LitemallGrouponId grouponId) {
        LitemallGrouponRulesAggregate rule = grouponRulesRepository.findById(rulesId);
        if (rule == null) {
            throw new IllegalArgumentException("Groupon rule not found");
        }

        if (!rule.isActive()) {
            return LitemallGrouponValidationResult.invalidRuleNotActive();
        }

        // Check if user already participated in any active groupon for this rule
        int participationsInRules = this.grouponRepository.countUserActiveParticipationsInRules(userId, rulesId);

        if (participationsInRules > 0) {
            return LitemallGrouponValidationResult.invalidAlreadyParticipated(
                    "User has already participated in " + participationsInRules + " active groupon(s) with these rules");
        }

        // 2. Si rejoindre un groupon existant, vérifier les restrictions spécifiques
        if (grouponId != null) {
            // Vérifier si l'utilisateur a déjà rejoint ce groupon spécifique
            boolean hasJoined = grouponRepository.hasJoin(userId, grouponId);
            if (hasJoined) {
                return LitemallGrouponValidationResult.invalidAlreadyJoined();
            }

            // Vérifier si l'utilisateur est le créateur du groupon
            boolean isCreator = grouponRepository.isGrouponCreator(userId, grouponId);
            if (isCreator) {
                return LitemallGrouponValidationResult.invalidUserNotEligibleOwnGroupon();
            }
        }
        return LitemallGrouponValidationResult.valid();
    }

    public LitemallGrouponRulesAggregate getGrouponRulesAggregate(LitemallGrouponRulesId rulesId) {
        return grouponRulesRepository.findById(rulesId);
    }
}
