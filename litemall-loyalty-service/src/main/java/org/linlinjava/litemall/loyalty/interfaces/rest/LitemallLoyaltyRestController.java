package org.linlinjava.litemall.loyalty.interfaces.rest;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.loyalty.application.LitemallLoyaltyOrchestratorService;
import org.linlinjava.litemall.loyalty.application.internal.LitemallLevelServiceLayer;
import org.linlinjava.litemall.loyalty.application.internal.LitemallLoyaltyServiceImpl;
import org.linlinjava.litemall.loyalty.application.internal.LitemallSignInServiceLayer;
import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallLoyaltyPointsAggregate;
import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallSignInAggregate;
import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallSystemLevelAggregate;
import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallUserLevelAggregate;
import org.linlinjava.litemall.loyalty.domain.model.commands.LitemallEarnPointsCommand;
import org.linlinjava.litemall.loyalty.domain.model.commands.LitemallSignInCommand;
import org.linlinjava.litemall.loyalty.domain.model.commands.LitemallSpendPointsCommand;
import org.linlinjava.litemall.loyalty.domain.model.repositories.LitemallPointsRepository;
import org.linlinjava.litemall.loyalty.domain.model.repositories.LitemallSignInRepository;
import org.linlinjava.litemall.loyalty.domain.model.repositories.LitemallUserLevelRepository;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.loyalty.interfaces.dtos.LoyaltyOperationDtoResponse;
import org.linlinjava.litemall.loyalty.interfaces.dtos.PointsBalanceDtoResponse;
import org.linlinjava.litemall.loyalty.interfaces.dtos.SignInDtoResponse;
import org.linlinjava.litemall.loyalty.interfaces.dtos.SignInHistoryDtoResponse;
import org.linlinjava.litemall.loyalty.interfaces.dtos.UserLevelDtoResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/srv/loyalty")
public class LitemallLoyaltyRestController {

    private final LitemallLoyaltyOrchestratorService orchestratorService;
    private final LitemallLoyaltyServiceImpl loyaltyService;
    private final LitemallSignInServiceLayer signInService;
    private final LitemallLevelServiceLayer levelService;
    private final LitemallPointsRepository pointsRepository;
    private final LitemallSignInRepository signInRepository;
    private final LitemallUserLevelRepository userLevelRepository;

    public LitemallLoyaltyRestController(LitemallLoyaltyOrchestratorService orchestratorService,
                                          LitemallLoyaltyServiceImpl loyaltyService,
                                          LitemallSignInServiceLayer signInService,
                                          LitemallLevelServiceLayer levelService,
                                          LitemallPointsRepository pointsRepository,
                                          LitemallSignInRepository signInRepository,
                                          LitemallUserLevelRepository userLevelRepository) {
        this.orchestratorService = orchestratorService;
        this.loyaltyService = loyaltyService;
        this.signInService = signInService;
        this.levelService = levelService;
        this.pointsRepository = pointsRepository;
        this.signInRepository = signInRepository;
        this.userLevelRepository = userLevelRepository;
    }

    /**
     * GET /srv/loyalty/{userId}/points/balance
     * Returns the current points balance for a user.
     */
    @GetMapping("/{userId}/points/balance")
    public ResponseEntity<PointsBalanceDtoResponse> getPointsBalance(@PathVariable Integer userId) {
        LitemallUserId uid = new LitemallUserId(userId);
        Integer balance = pointsRepository.getBalance(uid);
        int totalEarned = pointsRepository.sumPositiveByUserId(uid);
        PointsBalanceDtoResponse response = new PointsBalanceDtoResponse(userId, balance, totalEarned);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /srv/loyalty/{userId}/points/earn
     * Earn points for a user.
     */
    @PostMapping("/{userId}/points/earn")
    public ResponseEntity<LoyaltyOperationDtoResponse> earnPoints(
            @PathVariable Integer userId,
            @RequestBody LitemallEarnPointsCommand command) {
        command.setUserId(userId);
        try {
            LitemallLoyaltyPointsAggregate result = orchestratorService.earnPoints(command);
            LoyaltyOperationDtoResponse response = new LoyaltyOperationDtoResponse(
                    true, "Points earned successfully",
                    userId, "EARN_POINTS", result.getBalance(), command.getPoints());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("earnPoints failed for userId={}: {}", userId, e.getMessage());
            LoyaltyOperationDtoResponse response = new LoyaltyOperationDtoResponse(
                    false, e.getMessage(), userId, "EARN_POINTS", null, null);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        }
    }

    /**
     * POST /srv/loyalty/{userId}/points/spend
     * Spend points for a user.
     */
    @PostMapping("/{userId}/points/spend")
    public ResponseEntity<LoyaltyOperationDtoResponse> spendPoints(
            @PathVariable Integer userId,
            @RequestBody LitemallSpendPointsCommand command) {
        command.setUserId(userId);
        try {
            LitemallLoyaltyPointsAggregate result = orchestratorService.spendPoints(command);
            LoyaltyOperationDtoResponse response = new LoyaltyOperationDtoResponse(
                    true, "Points spent successfully",
                    userId, "SPEND_POINTS", result.getBalance(), -command.getPoints());
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            log.warn("spendPoints failed for userId={}: {}", userId, e.getMessage());
            LoyaltyOperationDtoResponse response = new LoyaltyOperationDtoResponse(
                    false, e.getMessage(), userId, "SPEND_POINTS", null, null);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } catch (Exception e) {
            log.error("spendPoints error for userId={}: {}", userId, e.getMessage());
            LoyaltyOperationDtoResponse response = new LoyaltyOperationDtoResponse(
                    false, e.getMessage(), userId, "SPEND_POINTS", null, null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * POST /srv/loyalty/{userId}/sign-in
     * Process daily sign-in for a user.
     */
    @PostMapping("/{userId}/sign-in")
    public ResponseEntity<SignInDtoResponse> signIn(@PathVariable Integer userId) {
        LitemallUserId uid = new LitemallUserId(userId);
        LitemallSignInCommand command = new LitemallSignInCommand(userId);
        LitemallSignInAggregate result = orchestratorService.signIn(command);

        boolean alreadySigned = result.getIntegral() == 0;
        int totalSignDays = signInService.getTotalSignDays(uid);

        SignInDtoResponse response = new SignInDtoResponse(
                !alreadySigned, userId, result.getIntegral(), alreadySigned, totalSignDays);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /srv/loyalty/{userId}/level
     * Get user's current VIP level.
     */
    @GetMapping("/{userId}/level")
    public ResponseEntity<UserLevelDtoResponse> getUserLevel(@PathVariable Integer userId) {
        LitemallUserId uid = new LitemallUserId(userId);
        Optional<LitemallUserLevelAggregate> levelOpt = userLevelRepository.findCurrentByUserId(uid);

        if (!levelOpt.isPresent()) {
            // Return a default level-0 response
            UserLevelDtoResponse response = new UserLevelDtoResponse(userId, (byte) 0, "Regular", 0, null, null);
            return ResponseEntity.ok(response);
        }

        LitemallUserLevelAggregate level = levelOpt.get();
        int currentXp = levelService.getBalanceSafe(uid);

        // Find next level threshold
        Integer nextLevelXp = null;
        List<LitemallSystemLevelAggregate> allLevels = userLevelRepository.findAllSystemLevels();
        for (LitemallSystemLevelAggregate def : allLevels) {
            if (def.getRequiredExperience() > currentXp) {
                nextLevelXp = def.getRequiredExperience();
                break;
            }
        }

        LitemallSystemLevelAggregate def = level.getLevelDefinition();
        String levelName = def != null ? def.getName() : "Unknown";
        java.math.BigDecimal discount = def != null ? def.getDiscount() : null;

        UserLevelDtoResponse response = new UserLevelDtoResponse(
                userId, level.getGrade(), levelName, currentXp, nextLevelXp, discount);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /srv/loyalty/{userId}/sign-in/history
     * Get sign-in history for a user.
     */
    @GetMapping("/{userId}/sign-in/history")
    public ResponseEntity<List<SignInHistoryDtoResponse>> getSignInHistory(@PathVariable Integer userId) {
        LitemallUserId uid = new LitemallUserId(userId);
        List<LitemallSignInAggregate> history = signInRepository.findByUserId(uid);
        List<SignInHistoryDtoResponse> response = history.stream()
                .map(s -> new SignInHistoryDtoResponse(
                        s.getSignId() != null ? s.getSignId().getId() : null,
                        s.getIntegral(),
                        s.getSignDate()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }
}
