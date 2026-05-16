package org.linlinjava.litemall.promotion.interfaces.rest;

import org.linlinjava.litemall.promotion.application.LitemallPromotionOrchestratorService;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallBargainAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallBargainUserAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallSeckillAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.LitemallCheckBargainStatusCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.LitemallCreateBargainSessionCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.LitemallHelpBargainCommand;
import org.linlinjava.litemall.promotion.domain.model.commands.LitemallJoinSeckillCommand;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallSeckillId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.linlinjava.litemall.promotion.interfaces.dtos.BargainDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.BargainSessionDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.PromotionOperationDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.SeckillDtoResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

import static org.linlinjava.litemall.promotion.interfaces.util.LitemallHttpResponseUtil.buildResponse;

@RestController
@RequestMapping("/srv/promotion")
public class LitemallPromotionRestController {

    private final LitemallPromotionOrchestratorService orchestratorService;

    @Autowired
    public LitemallPromotionRestController(LitemallPromotionOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    // =========================================================================
    // SECKILL ENDPOINTS
    // =========================================================================

    @GetMapping("/seckill/active")
    public ResponseEntity<List<SeckillDtoResponse>> getActiveSeckills() {
        List<LitemallSeckillAggregate> seckills = orchestratorService.getSeckillService()
                .getActiveSeckills();
        List<SeckillDtoResponse> response = seckills.stream()
                .map(this::toSeckillDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/seckill/{seckillId}")
    public ResponseEntity<SeckillDtoResponse> getSeckill(@PathVariable Integer seckillId) {
        return orchestratorService.getSeckillService()
                .getSeckill(new LitemallSeckillId(seckillId))
                .map(seckill -> ResponseEntity.ok(toSeckillDto(seckill)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/seckill/{seckillId}/join")
    public ResponseEntity<PromotionOperationDtoResponse> joinSeckill(
            @PathVariable Integer seckillId,
            @RequestHeader Integer userId,
            @RequestBody LitemallJoinSeckillCommand command) {

        command.setSeckillId(new LitemallSeckillId(seckillId));
        command.setUserId(new LitemallUserId(userId));

        LitemallPromotionOperationResult result = orchestratorService.joinSeckill(command);
        return buildResponse(result);
    }

    // =========================================================================
    // BARGAIN ENDPOINTS
    // =========================================================================

    @GetMapping("/bargain/active")
    public ResponseEntity<List<BargainDtoResponse>> getActiveBargains() {
        List<LitemallBargainAggregate> bargains = orchestratorService.getBargainService()
                .getActiveBargains();
        List<BargainDtoResponse> response = bargains.stream()
                .map(this::toBargainDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bargain/{bargainId}")
    public ResponseEntity<BargainDtoResponse> getBargain(@PathVariable Integer bargainId) {
        return orchestratorService.getBargainService()
                .getBargain(new LitemallBargainId(bargainId))
                .map(bargain -> ResponseEntity.ok(toBargainDto(bargain)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/bargain/{bargainId}/start")
    public ResponseEntity<PromotionOperationDtoResponse> startBargain(
            @PathVariable Integer bargainId,
            @RequestHeader Integer userId) {

        LitemallCreateBargainSessionCommand command = new LitemallCreateBargainSessionCommand(
                new LitemallUserId(userId),
                new LitemallBargainId(bargainId)
        );

        LitemallPromotionOperationResult result = orchestratorService.createBargainSession(command);
        return buildResponse(result);
    }

    @PostMapping("/bargain/session/{bargainUserId}/help")
    public ResponseEntity<PromotionOperationDtoResponse> helpBargain(
            @PathVariable Integer bargainUserId,
            @RequestHeader Integer helperId) {

        LitemallBargainUserId bargainUserIdVO = new LitemallBargainUserId(bargainUserId);

        // Retrieve session to get bargainId
        LitemallBargainUserAggregate session = orchestratorService.getBargainService()
                .getBargainSession(bargainUserIdVO)
                .orElse(null);

        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        LitemallHelpBargainCommand command = new LitemallHelpBargainCommand(
                new LitemallUserId(helperId),
                bargainUserIdVO,
                session.getBargainId()
        );

        LitemallPromotionOperationResult result = orchestratorService.helpBargain(command);
        return buildResponse(result);
    }

    @GetMapping("/bargain/session/{bargainUserId}")
    public ResponseEntity<BargainSessionDtoResponse> getBargainSession(
            @PathVariable Integer bargainUserId) {

        LitemallBargainUserId bargainUserIdVO = new LitemallBargainUserId(bargainUserId);

        return orchestratorService.getBargainService()
                .getBargainSession(bargainUserIdVO)
                .map(session -> {
                    int helpCount = orchestratorService.getBargainService()
                            .getHelpCount(bargainUserIdVO);
                    return ResponseEntity.ok(toBargainSessionDto(session, helpCount));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================================================
    // DTO MAPPING
    // =========================================================================

    private SeckillDtoResponse toSeckillDto(LitemallSeckillAggregate seckill) {
        return SeckillDtoResponse.builder()
                .seckillId(seckill.getSeckillId() != null ? seckill.getSeckillId().getId() : null)
                .goodsName(seckill.getGoodsName())
                .price(seckill.getPrice() != null ? seckill.getPrice().getAmount() : null)
                .stock(seckill.getStock())
                .sales(seckill.getSales())
                .quota(seckill.getQuota())
                .timeSlot(seckill.getSeckillTime())
                .status(seckill.getStatus() != null ? seckill.getStatus().getDisplayName() : null)
                .startTime(seckill.getStartTime())
                .stopTime(seckill.getStopTime())
                .build();
    }

    private BargainDtoResponse toBargainDto(LitemallBargainAggregate bargain) {
        return BargainDtoResponse.builder()
                .bargainId(bargain.getBargainId() != null ? bargain.getBargainId().getId() : null)
                .title(bargain.getTitle())
                .price(bargain.getPrice() != null ? bargain.getPrice().getAmount() : null)
                .minPrice(bargain.getMinPrice() != null ? bargain.getMinPrice().getAmount() : null)
                .stock(bargain.getStock())
                .status(bargain.getStatus() != null ? bargain.getStatus().getDisplayName() : null)
                .startTime(bargain.getStartTime())
                .stopTime(bargain.getStopTime())
                .bargainNum(bargain.getBargainNum())
                .peopleNum(bargain.getPeopleNum())
                .build();
    }

    private BargainSessionDtoResponse toBargainSessionDto(LitemallBargainUserAggregate session,
                                                           int helpCount) {
        return BargainSessionDtoResponse.builder()
                .bargainUserId(session.getBargainUserId() != null ?
                        session.getBargainUserId().getId() : null)
                .userId(session.getUserId() != null ? session.getUserId().getId() : null)
                .bargainId(session.getBargainId() != null ? session.getBargainId().getId() : null)
                .currentPrice(session.getBargainPrice() != null ?
                        session.getBargainPrice().getAmount() : null)
                .targetPrice(session.getBargainPriceMin() != null ?
                        session.getBargainPriceMin().getAmount() : null)
                .status(session.getStatus() != null ? session.getStatus().getDisplayName() : null)
                .helpCount(helpCount)
                .build();
    }
}