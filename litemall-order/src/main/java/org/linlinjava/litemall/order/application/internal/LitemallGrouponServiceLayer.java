package org.linlinjava.litemall.order.application.internal;


import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponRulesAggregate;
import org.linlinjava.litemall.order.domain.model.domainservices.groupon.LitemallGrouponValidationResult;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRulesRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponRulesId;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallGrouponStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class LitemallGrouponServiceLayer {
    private final LitemallGrouponRulesRepository grouponRulesRepository;
    private final LitemallGrouponRepository grouponRepository;

    @Autowired
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

        if(grouponLinkId != null && grouponLinkId > 0){
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
                return LitemallGrouponValidationResult.grouponJoin();
            }
            //（2）Not allowed to participate in group buying organized by oneself
            LitemallUserId userIdentity = new LitemallUserId(userId);
            LitemallGrouponAggregate grouponAggregate = grouponRepository.findByUserId(grouponId, userIdentity);
           /* if(grouponAggregate.getCreatorUserId().equals(userIdentity)){
                return LitemallGrouponValidationResult.grouponJoin();
            }*/
            if(grouponAggregate !=null) {
                if(grouponAggregate.getCreatorUserId().equals(userId)){
                    return LitemallGrouponValidationResult.grouponJoin();
                }
            }
            return LitemallGrouponValidationResult.valid();
        }
        return LitemallGrouponValidationResult.valid();
    }

    public BigDecimal getGrouponDiscount(LitemallGrouponRulesId grouponRulesId){
        LitemallGrouponRulesAggregate rulesAggregate = grouponRulesRepository.findById(grouponRulesId);
        if(rulesAggregate!= null){
            return rulesAggregate.getDiscount();
        }

    }
}
