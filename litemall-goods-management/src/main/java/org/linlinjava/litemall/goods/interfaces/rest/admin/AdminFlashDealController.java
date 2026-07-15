package org.linlinjava.litemall.goods.interfaces.rest.admin;

import jakarta.validation.constraints.NotNull;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallSeckill;
import org.linlinjava.litemall.goods.application.deals.FlashDealService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Admin flash-deal surface ({@code /srv/private/admin/**} → machine token +
 * ROLE_ADMIN; rides gateway-admin's goods catch-all route). Contract:
 * {@code docs/handoff-gateway-admin-flash-deals.md}. Errnos: 650 invalid,
 * 651 conflict (overlap / live-immutable), 652 CJ goods refused.
 *
 * <p>Lifecycle is scheduler-owned: create/update only author intent
 * (enabled + window + price); {@code live} in every view reflects
 * {@code price_swapped}. Disabling (status=0) or deleting a live deal is
 * allowed — the scheduler unwinds the price swap on its next tick (≤60s).
 */
@RestController
@RequestMapping("/srv/private/admin/deal")
@Validated
public class AdminFlashDealController {

    public static class DealUpsertRequest {
        public Integer id;
        public Integer goodsId;
        public BigDecimal dealPrice;
        public LocalDateTime startTime;
        public LocalDateTime stopTime;
        public Integer stock;
        public Boolean enabled;
    }

    private final FlashDealService flashDealService;

    public AdminFlashDealController(FlashDealService flashDealService) {
        this.flashDealService = flashDealService;
    }

    @GetMapping("/list")
    public Object list(@RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "20") Integer limit) {
        return ResponseUtil.ok(flashDealService.adminList(page, limit));
    }

    @GetMapping("/read")
    public Object read(@NotNull @RequestParam Integer id) {
        LitemallSeckill deal = flashDealService.findById(id);
        if (deal == null) {
            return ResponseUtil.badArgumentValue();
        }
        return ResponseUtil.ok(flashDealService.toAdminView(deal));
    }

    @PostMapping("/create")
    public Object create(@RequestBody DealUpsertRequest request) {
        FlashDealService.Result result = flashDealService.create(
                request.goodsId, request.dealPrice, request.startTime, request.stopTime, request.stock);
        return toResponse(result);
    }

    @PostMapping("/update")
    public Object update(@RequestBody DealUpsertRequest request) {
        if (request.id == null) {
            return ResponseUtil.badArgument();
        }
        Byte status = request.enabled == null ? null : (byte) (request.enabled ? 1 : 0);
        FlashDealService.Result result = flashDealService.update(
                request.id, request.dealPrice, request.startTime, request.stopTime, request.stock, status);
        return toResponse(result);
    }

    @PostMapping("/delete")
    public Object delete(@RequestBody DealUpsertRequest request) {
        if (request.id == null) {
            return ResponseUtil.badArgument();
        }
        return toResponse(flashDealService.delete(request.id));
    }

    private Object toResponse(FlashDealService.Result result) {
        if (result.error() != null) {
            return ResponseUtil.fail(result.errno(), result.error());
        }
        return ResponseUtil.ok(flashDealService.toAdminView(result.deal()));
    }
}
