package org.linlinjava.litemall.promotion.application;

import org.linlinjava.litemall.promotion.application.internal.LitemallBargainServiceImpl;
import org.linlinjava.litemall.promotion.application.internal.LitemallCampaignSchedulingServiceImpl;
import org.linlinjava.litemall.promotion.application.internal.LitemallCampaignServiceImpl;
import org.linlinjava.litemall.promotion.application.internal.LitemallCombinationServiceImpl;
import org.linlinjava.litemall.promotion.application.internal.LitemallCouponServiceImpl;
import org.linlinjava.litemall.promotion.application.internal.LitemallSeckillServiceImpl;
import org.linlinjava.litemall.promotion.domain.model.commands.campaign.LitemallActivateCampaignCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.campaign.LitemallCampaignFromCategoryCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.campaign.LitemallDefineCampaignCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.campaign.LitemallEvaluateCampaignCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.LitemallCheckBargainStatusCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.LitemallCreateBargainSessionCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.LitemallHelpBargainCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.LitemallJoinSeckillCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallExchangeCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallGrantCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallIssueCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallReceiveCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallRedeemCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallReleaseCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallUpdateCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@Transactional
public class LitemallPromotionOrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(LitemallPromotionOrchestratorService.class);

    private final LitemallSeckillServiceImpl seckillService;
    private final LitemallBargainServiceImpl bargainService;
    private final LitemallCouponServiceImpl couponService;
    private final LitemallCombinationServiceImpl combinationService;
    private final LitemallCampaignServiceImpl campaignService;
    private final LitemallCampaignSchedulingServiceImpl campaignSchedulingService;

    @Autowired
    public LitemallPromotionOrchestratorService(LitemallSeckillServiceImpl seckillService,
                                                 LitemallBargainServiceImpl bargainService,
                                                 LitemallCouponServiceImpl couponService,
                                                 LitemallCombinationServiceImpl combinationService,
                                                 LitemallCampaignServiceImpl campaignService,
                                                 LitemallCampaignSchedulingServiceImpl campaignSchedulingService) {
        this.seckillService = seckillService;
        this.bargainService = bargainService;
        this.couponService = couponService;
        this.combinationService = combinationService;
        this.campaignService = campaignService;
        this.campaignSchedulingService = campaignSchedulingService;
    }

    public enum PromotionAction {
        JOIN_SECKILL, CREATE_BARGAIN, HELP_BARGAIN, CHECK_BARGAIN_STATUS
    }

    /**
     * Main entry point for all promotion actions.
     */
    public LitemallPromotionOperationResult performAction(PromotionAction action, Object data) {
        logger.info("Processing promotion action: {}", action);

        switch (action) {
            case JOIN_SECKILL:
                return handleJoinSeckill((LitemallJoinSeckillCommand) data);

            case CREATE_BARGAIN:
                return handleCreateBargainSession((LitemallCreateBargainSessionCommand) data);

            case HELP_BARGAIN:
                return handleHelpBargain((LitemallHelpBargainCommand) data);

            case CHECK_BARGAIN_STATUS:
                return handleCheckBargainStatus((LitemallCheckBargainStatusCommand) data);

            default:
                return LitemallPromotionOperationResult.failed(null,
                        "Unknown promotion action: " + action);
        }
    }

    // =========================================================================
    // ACTION HANDLERS
    // =========================================================================

    private LitemallPromotionOperationResult handleJoinSeckill(LitemallJoinSeckillCommand command) {
        try {
            return seckillService.joinSeckill(command);
        } catch (IllegalArgumentException e) {
            return LitemallPromotionOperationResult.seckillJoinFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error joining seckill", e);
            return LitemallPromotionOperationResult.seckillJoinFailed(
                    "System error: " + e.getMessage());
        }
    }

    private LitemallPromotionOperationResult handleCreateBargainSession(
            LitemallCreateBargainSessionCommand command) {
        try {
            return bargainService.createBargainSession(command);
        } catch (IllegalArgumentException e) {
            return LitemallPromotionOperationResult.bargainSessionFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating bargain session", e);
            return LitemallPromotionOperationResult.bargainSessionFailed(
                    "System error: " + e.getMessage());
        }
    }

    private LitemallPromotionOperationResult handleHelpBargain(LitemallHelpBargainCommand command) {
        try {
            return bargainService.helpBargain(command);
        } catch (IllegalArgumentException e) {
            return LitemallPromotionOperationResult.helpBargainFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error applying bargain help", e);
            return LitemallPromotionOperationResult.helpBargainFailed(
                    "System error: " + e.getMessage());
        }
    }

    private LitemallPromotionOperationResult handleCheckBargainStatus(
            LitemallCheckBargainStatusCommand command) {
        try {
            return bargainService.getBargainSession(command.getBargainUserId())
                    .map(session -> {
                        int helpCount = bargainService.getHelpCount(command.getBargainUserId());
                        Map<String, Object> resultData = new HashMap<>();
                        resultData.put("bargainUserId", session.getBargainUserId() != null ?
                                session.getBargainUserId().getId() : null);
                        resultData.put("userId", session.getUserId().getId());
                        resultData.put("bargainId", session.getBargainId().getId());
                        resultData.put("currentPrice", session.getBargainPrice().getAmount());
                        resultData.put("targetPrice", session.getBargainPriceMin().getAmount());
                        resultData.put("status", session.getStatus().getDisplayName());
                        resultData.put("helpCount", helpCount);
                        return LitemallPromotionOperationResult.bargainStatusChecked(resultData);
                    })
                    .orElse(LitemallPromotionOperationResult.failed(
                            LitemallPromotionOperationResult.OperationType.CHECK_BARGAIN_STATUS,
                            "Bargain session not found"));
        } catch (Exception e) {
            logger.error("Error checking bargain status", e);
            return LitemallPromotionOperationResult.failed(
                    LitemallPromotionOperationResult.OperationType.CHECK_BARGAIN_STATUS,
                    "System error: " + e.getMessage());
        }
    }

    // =========================================================================
    // CONVENIENCE METHODS
    // =========================================================================

    public LitemallPromotionOperationResult joinSeckill(LitemallJoinSeckillCommand command) {
        return performAction(PromotionAction.JOIN_SECKILL, command);
    }

    public LitemallPromotionOperationResult createBargainSession(LitemallCreateBargainSessionCommand command) {
        return performAction(PromotionAction.CREATE_BARGAIN, command);
    }

    public LitemallPromotionOperationResult helpBargain(LitemallHelpBargainCommand command) {
        return performAction(PromotionAction.HELP_BARGAIN, command);
    }

    public LitemallPromotionOperationResult checkBargainStatus(LitemallCheckBargainStatusCommand command) {
        return performAction(PromotionAction.CHECK_BARGAIN_STATUS, command);
    }

    // ----- Coupon vertical -----

    public LitemallPromotionOperationResult issueCoupon(LitemallIssueCouponCommand command) {
        try {
            return couponService.issueCoupon(command);
        } catch (IllegalArgumentException e) {
            return LitemallPromotionOperationResult.couponIssueFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error issuing coupon", e);
            return LitemallPromotionOperationResult.couponIssueFailed("System error: " + e.getMessage());
        }
    }

    public LitemallPromotionOperationResult receiveCoupon(LitemallReceiveCouponCommand command) {
        try {
            return couponService.receiveCoupon(command);
        } catch (IllegalArgumentException e) {
            return LitemallPromotionOperationResult.couponReceiveFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error receiving coupon", e);
            return LitemallPromotionOperationResult.couponReceiveFailed("System error: " + e.getMessage());
        }
    }

    public LitemallPromotionOperationResult redeemCoupon(LitemallRedeemCouponCommand command) {
        try {
            return couponService.redeemCoupon(command);
        } catch (IllegalArgumentException e) {
            return LitemallPromotionOperationResult.couponRedeemFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error redeeming coupon", e);
            return LitemallPromotionOperationResult.couponRedeemFailed("System error: " + e.getMessage());
        }
    }

    public LitemallPromotionOperationResult exchangeCoupon(LitemallExchangeCouponCommand command) {
        try {
            return couponService.exchangeCoupon(command);
        } catch (IllegalArgumentException e) {
            return LitemallPromotionOperationResult.couponExchangeFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error exchanging coupon", e);
            return LitemallPromotionOperationResult.couponExchangeFailed("System error: " + e.getMessage());
        }
    }

    public LitemallPromotionOperationResult releaseCoupon(LitemallReleaseCouponCommand command) {
        try {
            return couponService.releaseCoupon(command);
        } catch (IllegalArgumentException e) {
            return LitemallPromotionOperationResult.couponReleaseFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error releasing coupon", e);
            return LitemallPromotionOperationResult.couponReleaseFailed("System error: " + e.getMessage());
        }
    }

    public LitemallPromotionOperationResult updateCoupon(LitemallCouponId couponId,
                                                         LitemallUpdateCouponCommand command) {
        try {
            return couponService.updateCoupon(couponId, command);
        } catch (IllegalArgumentException e) {
            return LitemallPromotionOperationResult.couponUpdateFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error updating coupon", e);
            return LitemallPromotionOperationResult.couponUpdateFailed("System error: " + e.getMessage());
        }
    }

    public LitemallPromotionOperationResult deleteCoupon(LitemallCouponId couponId) {
        try {
            return couponService.deleteCoupon(couponId);
        } catch (IllegalArgumentException e) {
            return LitemallPromotionOperationResult.couponDeleteFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error deleting coupon", e);
            return LitemallPromotionOperationResult.couponDeleteFailed("System error: " + e.getMessage());
        }
    }

    public LitemallPromotionOperationResult grantCoupon(LitemallGrantCouponCommand command) {
        try {
            return couponService.grantCoupon(command);
        } catch (IllegalArgumentException e) {
            return LitemallPromotionOperationResult.couponGrantFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error granting coupon", e);
            return LitemallPromotionOperationResult.couponGrantFailed("System error: " + e.getMessage());
        }
    }

    // ----- Combination (group-buy) campaign-definition vertical -----

    public LitemallPromotionOperationResult defineCombination(
            org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallDefineCombinationCommand command) {
        try {
            return combinationService.defineCombination(command);
        } catch (IllegalArgumentException e) {
            return LitemallPromotionOperationResult.combinationDefineFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error defining combination campaign", e);
            return LitemallPromotionOperationResult.combinationDefineFailed("System error: " + e.getMessage());
        }
    }

    public LitemallPromotionOperationResult activateCombination(
            org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallActivateCombinationCommand command) {
        try {
            return combinationService.activateCombination(command);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return LitemallPromotionOperationResult.combinationStateChangeFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error activating combination campaign", e);
            return LitemallPromotionOperationResult.combinationStateChangeFailed("System error: " + e.getMessage());
        }
    }

    public LitemallPromotionOperationResult expireCombination(
            org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallExpireCombinationCommand command) {
        try {
            return combinationService.expireCombination(command);
        } catch (Exception e) {
            logger.error("Error expiring combination campaign", e);
            return LitemallPromotionOperationResult.combinationStateChangeFailed("System error: " + e.getMessage());
        }
    }

    public LitemallPromotionOperationResult updateCombination(LitemallCombinationId combinationId,
            org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallUpdateCombinationCommand command) {
        try {
            return combinationService.updateCombination(combinationId, command);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return LitemallPromotionOperationResult.combinationStateChangeFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error updating combination campaign", e);
            return LitemallPromotionOperationResult.combinationStateChangeFailed("System error: " + e.getMessage());
        }
    }

    public LitemallPromotionOperationResult deleteCombination(LitemallCombinationId combinationId) {
        try {
            return combinationService.deleteCombination(combinationId);
        } catch (Exception e) {
            logger.error("Error deleting combination campaign", e);
            return LitemallPromotionOperationResult.combinationStateChangeFailed("System error: " + e.getMessage());
        }
    }

    public LitemallPromotionOperationResult startGroup(
            org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallStartGroupCommand command) {
        try {
            return combinationService.startGroup(command);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return LitemallPromotionOperationResult.groupStartFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error starting group", e);
            return LitemallPromotionOperationResult.groupStartFailed("System error: " + e.getMessage());
        }
    }

    public LitemallPromotionOperationResult joinGroup(
            org.linlinjava.litemall.promotion.domain.model.commands.combination.LitemallJoinGroupCommand command) {
        try {
            return combinationService.joinGroup(command);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return LitemallPromotionOperationResult.groupJoinFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error joining group", e);
            return LitemallPromotionOperationResult.groupJoinFailed("System error: " + e.getMessage());
        }
    }

    // ----- Campaign (algorithmic targeting) vertical -----

    public LitemallPromotionOperationResult defineCampaign(LitemallDefineCampaignCommand command) {
        try {
            return campaignService.defineCampaign(command);
        } catch (IllegalArgumentException e) {
            return LitemallPromotionOperationResult.campaignDefineFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error defining campaign", e);
            return LitemallPromotionOperationResult.campaignDefineFailed("System error: " + e.getMessage());
        }
    }

    public LitemallPromotionOperationResult activateCampaign(LitemallActivateCampaignCommand command) {
        try {
            return campaignService.activateCampaign(command);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return LitemallPromotionOperationResult.campaignStateChangeFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error activating campaign", e);
            return LitemallPromotionOperationResult.campaignStateChangeFailed("System error: " + e.getMessage());
        }
    }

    public LitemallPromotionOperationResult evaluateCampaign(LitemallEvaluateCampaignCommand command) {
        try {
            return campaignService.evaluateCampaign(command);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return LitemallPromotionOperationResult.campaignEvaluateFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error evaluating campaign", e);
            return LitemallPromotionOperationResult.campaignEvaluateFailed("System error: " + e.getMessage());
        }
    }

    public LitemallPromotionOperationResult campaignFromCategory(LitemallCampaignFromCategoryCommand command,
                                                                 String postedBy) {
        try {
            return campaignSchedulingService.fromCategory(command, postedBy);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return LitemallPromotionOperationResult.campaignFromCategoryFailed(e.getMessage());
        } catch (Exception e) {
            logger.error("Error composing category campaign", e);
            return LitemallPromotionOperationResult.campaignFromCategoryFailed("System error: " + e.getMessage());
        }
    }

    // Delegate read-only methods
    public LitemallSeckillServiceImpl getSeckillService() {
        return seckillService;
    }

    public LitemallCampaignServiceImpl getCampaignService() {
        return campaignService;
    }

    public LitemallBargainServiceImpl getBargainService() {
        return bargainService;
    }

    public LitemallCouponServiceImpl getCouponService() {
        return couponService;
    }

    public LitemallCombinationServiceImpl getCombinationService() {
        return combinationService;
    }
}
