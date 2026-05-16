package org.linlinjava.litemall.order.application.internal;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import lombok.Getter;
import org.linlinjava.litemall.core.qcode.QCodeService;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponRulesAggregate;
import org.linlinjava.litemall.order.domain.service.groupon.LitemallGrouponValidationResult;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRulesRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponRulesId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallGrouponStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LitemallGrouponServiceLayer {

    private final LitemallGrouponRulesRepository grouponRulesRepository;
    @Getter
    private final LitemallGrouponRepository grouponRepository;
    @Autowired
    private QCodeService qCodeService;

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
                if(grouponAggregate.getCreatorUserId().getId().equals(userId)){
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

    /**
     *
     * @param rulesId
     * @return
     */
    public LitemallGrouponRulesAggregate getGrouponRulesAggregate(LitemallGrouponRulesId rulesId) {
        return grouponRulesRepository.findById(rulesId);
    }

    /**
     *
     * @param orderId
     * @return
     */
    public LitemallGrouponAggregate getGrouponAggregateByOrderId(LitemallOrderId orderId) {
        return  grouponRepository.getGrouponByOrderId(orderId);
    }

    /**
     *
     * @param grouponRulesId
     * @return
     */
    public LitemallGrouponRulesAggregate getGrouponRulesById(LitemallGrouponRulesId grouponRulesId) {
        return  grouponRulesRepository.findById(grouponRulesId);
    }

    /**
     * Creates a groupon order based on the command
     * @param cmdGrouponLinkId The order cmd grouponLinkId containing groupon information
     * @param cmdUserId The user ID
     * @param cmdGrouponRulesId The groupon rules ID
     * @param orderId The order ID
     * @return The created groupon link ID, or null if not a groupon purchase
     */
    public Integer createGrouponOrder(Integer cmdGrouponLinkId,  Integer cmdUserId,
                                      Integer cmdGrouponRulesId, LitemallOrderId orderId) {
        if (!isGrouponPurchase(cmdGrouponRulesId, cmdGrouponLinkId)) {
            return null;
        }

        LitemallGrouponAggregate grouponAggregate = createBaseGrouponAggregate(
                cmdGrouponRulesId, cmdUserId, orderId.getId());



        if (isJoiningExistingGroupon(cmdGrouponLinkId)) {
            return handleExistingGrouponJoin(grouponAggregate, cmdGrouponLinkId);
        } else {
            return handleNewGrouponCreation(grouponAggregate, cmdUserId);
        }
    }

    /**
     *
     * @param grouponAggregate
     * @param grouponRulesAggregate
     */
    public void updateGrouponAfterPayment(LitemallGrouponAggregate grouponAggregate, LitemallGrouponRulesAggregate grouponRulesAggregate){

        //Shared images are created only if the originator
        if (grouponAggregate.getGrouponId().getId() == 0) {
            LitemallGroupon groupon = grouponRepository.convertToDataModel(grouponAggregate);
            String url = qCodeService.createGrouponShareImage(grouponRulesAggregate.getGoodsName(), grouponRulesAggregate.getPicUrl(), groupon);
            groupon.setShareUrl(url);
        }
        //grouponAggregate.setGrouponStatus(GrouponConstant.STATUS_ON);
        grouponAggregate.setGrouponStatus(LitemallGrouponStatus.STATUS_ON);
        if (grouponRepository.updateById(grouponAggregate) == 0) {
            throw new RuntimeException("Update data has expired");
        }

        List<LitemallGrouponAggregate> grouponList = grouponRepository.getJoinRecord(grouponAggregate.getGrouponId());
        if (grouponAggregate.getGrouponId().getId() != 0 && (grouponList.size() >= grouponRulesAggregate.getDiscountMember() - 1)) {
            for (LitemallGrouponAggregate grouponActivity : grouponList) {
                //grouponActivity.setGrouponStatus(GrouponConstant.STATUS_SUCCEED);
                grouponActivity.setGrouponStatus(LitemallGrouponStatus.STATUS_SUCCEED);
                grouponRepository.updateById(grouponActivity);
            }

            LitemallGrouponAggregate grouponSource = grouponRepository.findById(grouponAggregate.getGrouponId());
            grouponSource.setGrouponStatus(LitemallGrouponStatus.STATUS_SUCCEED);
            grouponRepository.updateById(grouponSource);
        }
    }


    /**
     *
     * @param cmdRulesId
     * @param cmdUserId
     * @param cmdOrderId
     * @return
     */
    private LitemallGrouponAggregate createBaseGrouponAggregate(Integer cmdRulesId, Integer  cmdUserId, Integer cmdOrderId) {
        LitemallGrouponRulesId rulesId = new LitemallGrouponRulesId(cmdRulesId);
        LitemallUserId userId = new LitemallUserId(cmdUserId);
        LitemallOrderId orderId = new LitemallOrderId(cmdOrderId);

        LitemallGrouponAggregate grouponAggregate = new LitemallGrouponAggregate(rulesId, userId, orderId);

        grouponAggregate.setOrderId(orderId);
        grouponAggregate.setGrouponStatus(LitemallGrouponStatus.STATUS_NONE);
        grouponAggregate.setCreatorUserId(userId);
        grouponAggregate.setGrouponRulesId(rulesId);
        return grouponAggregate;
    }


    /**
     *
     * @param userId
     * @param rulesId
     * @param grouponId
     * @return
     */
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

    /**
     *
     * @param grouponAggregate
     * @param grouponLinkId
     * @return
     */
    private Integer handleExistingGrouponJoin(LitemallGrouponAggregate grouponAggregate, Integer grouponLinkId) {
        LitemallGrouponAggregate baseGrouponAggregate = grouponRepository.findById(new LitemallGrouponId(grouponLinkId));

        grouponAggregate.setCreatorUserId(baseGrouponAggregate.getCreatorUserId());
        grouponAggregate.setGrouponId(new LitemallGrouponId(grouponLinkId));
        grouponAggregate.setShareUrl(baseGrouponAggregate.getShareUrl());

        grouponRepository.saveGroupon(grouponAggregate);
        return grouponLinkId;
    }

    /**
     *
     * @param grouponAggregate
     * @param userId
     * @return
     */
    private Integer handleNewGrouponCreation(LitemallGrouponAggregate grouponAggregate, Integer userId) {
        grouponAggregate.setCreatorUserId(new LitemallUserId(userId));
        grouponAggregate.setCreatorUserTime(LocalDateTime.now());
        grouponAggregate.setGrouponId(new LitemallGrouponId(0));

        grouponRepository.saveGroupon(grouponAggregate);
        return grouponAggregate.getGrouponId().getId();
    }


    // ====================
    // UTILITY METHODS
    // ====================
    private boolean isGrouponPurchase(Integer grouponRulesId, Integer grouponLinkId) {
        return grouponRulesId!= null && grouponLinkId > 0;
    }

    private boolean isJoiningExistingGroupon(Integer grouponLinkId) {
        return grouponLinkId != null && grouponLinkId > 0;
    }



}
