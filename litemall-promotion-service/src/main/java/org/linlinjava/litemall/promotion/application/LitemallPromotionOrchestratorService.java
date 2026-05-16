package org.linlinjava.litemall.promotion.application;

import org.linlinjava.litemall.promotion.application.internal.LitemallBargainServiceImpl;
import org.linlinjava.litemall.promotion.application.internal.LitemallSeckillServiceImpl;
import org.linlinjava.litemall.promotion.domain.model.commands.LitemallCheckBargainStatusCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.LitemallCreateBargainSessionCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.LitemallHelpBargainCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.LitemallJoinSeckillCommand;
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

    @Autowired
    public LitemallPromotionOrchestratorService(LitemallSeckillServiceImpl seckillService,
                                                 LitemallBargainServiceImpl bargainService) {
        this.seckillService = seckillService;
        this.bargainService = bargainService;
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

    // Delegate read-only methods
    public LitemallSeckillServiceImpl getSeckillService() {
        return seckillService;
    }

    public LitemallBargainServiceImpl getBargainService() {
        return bargainService;
    }
}
