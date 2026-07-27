package org.linlinjava.litemall.goods.application.deals;

import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallSeckill;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.db.service.LitemallSeckillService;
import org.linlinjava.litemall.goods.domain.deals.DealMath;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flash-deal authoring + queries over the V9 {@code litemall_seckill} table
 * (V38 added the price-swap lifecycle columns). Admin semantics:
 * {@code status=1} = enabled (the lifecycle scheduler swaps the goods price
 * when the window opens), {@code status=0} = disabled (scheduler unwinds any
 * live swap on its next tick). "Live" — the deal price currently on the goods
 * row — is exactly {@code price_swapped=1}, maintained ONLY by
 * {@link FlashDealLifecycleTask}.
 *
 * <p>Design (ADR {@code docs/adr-flash-deals-price-swap.md}): the deal is
 * realized by swapping {@code litemall_goods.retail_price} AND every
 * {@code litemall_goods_product.price} SKU row (checkout money reads the SKU
 * rows — V40), so checkout, cart, freight and the Phase-A discount signals all
 * see the deal with ZERO order-service changes — a deliberate deviation from
 * crmeb's separate seckill purchase path (promotion's {@code /seckill/join}
 * stock-reservation flow is bypassed; its read endpoints keep working since
 * they read this same table). CJ goods are LIVE again (Wave 12, unparking
 * a82a19e0e): the deal price is floored at the REAL captured wholesale cost
 * ({@code litemall_goods.cost}, landed by the V45 sync) — below the floor is
 * 650 naming the floor; 652 is narrowed to "no captured cost, basis unknown".
 * The uniform ×margin pricing means the goods-level floor keeps every
 * proportionally-swapped SKU at or above its own variant cost.
 */
@Service
public class FlashDealService {

    /** Validation outcome: {@code error != null} XOR {@code deal != null}. */
    public record Result(String error, Integer errno, LitemallSeckill deal) {
        static Result fail(Integer errno, String message) {
            return new Result(message, errno, null);
        }

        static Result ok(LitemallSeckill deal) {
            return new Result(null, null, deal);
        }
    }

    private static final int ERRNO_INVALID = 650;
    private static final int ERRNO_CONFLICT = 651;
    private static final int ERRNO_CJ = 652;

    private final LitemallSeckillService seckillService;
    private final LitemallGoodsService goodsService;

    public FlashDealService(LitemallSeckillService seckillService, LitemallGoodsService goodsService) {
        this.seckillService = seckillService;
        this.goodsService = goodsService;
    }

    public Result create(Integer goodsId, BigDecimal dealPrice, LocalDateTime start, LocalDateTime stop,
                         Integer stock) {
        LitemallGoods goods = goodsId == null ? null : goodsService.findById(goodsId);
        if (goods == null) {
            return Result.fail(ERRNO_INVALID, "goods " + goodsId + " not found");
        }
        Result floorError = validateCjFloor(goods, dealPrice);
        if (floorError != null) {
            return floorError;
        }
        String windowError = validateWindow(dealPrice, goods.getRetailPrice(), start, stop);
        if (windowError != null) {
            return Result.fail(ERRNO_INVALID, windowError);
        }
        if (seckillService.hasOverlapping(goodsId, start, stop, null)) {
            return Result.fail(ERRNO_CONFLICT, "an enabled deal already overlaps this window for goods " + goodsId);
        }
        LitemallSeckill deal = new LitemallSeckill();
        deal.setGoodsId(goodsId);
        deal.setGoodsName(goods.getName());
        deal.setPicUrl(goods.getPicUrl());
        deal.setPrice(dealPrice);
        deal.setStock(stock == null ? 0 : stock);
        deal.setStartTime(start);
        deal.setStopTime(stop);
        deal.setStatus((byte) 1);
        // The V9 table's crmeb-era columns are NOT NULL and the mapper insert lists every one —
        // zero them (we use window-based deals, not crmeb's hour-slot "time" model).
        deal.setCost(BigDecimal.ZERO);
        deal.setSales(0);
        deal.setQuota(0);
        deal.setQuotaShow(0);
        deal.setTime((byte) 0);
        deal.setIsPostage(false);
        deal.setPostage(BigDecimal.ZERO);
        deal.setTempId(0);
        deal.setWeight(BigDecimal.ZERO);
        deal.setVolume(BigDecimal.ZERO);
        deal.setSort(0);
        deal.setIsDel(false);
        seckillService.add(deal);
        return Result.ok(deal);
    }

    public Result update(Integer id, BigDecimal dealPrice, LocalDateTime start, LocalDateTime stop,
                         Integer stock, Byte status) {
        LitemallSeckill deal = seckillService.findById(id);
        if (deal == null) {
            return Result.fail(ERRNO_INVALID, "deal " + id + " not found");
        }
        boolean live = Boolean.TRUE.equals(deal.getPriceSwapped());
        if (live && (dealPrice != null || start != null)) {
            // The swapped goods row carries the current price; changing it mid-flight would
            // desynchronize charged vs displayed. Disable first (scheduler unwinds), then edit.
            return Result.fail(ERRNO_CONFLICT, "deal " + id + " is live — disable it before changing price or start");
        }
        BigDecimal effPrice = dealPrice != null ? dealPrice : deal.getPrice();
        LocalDateTime effStart = start != null ? start : deal.getStartTime();
        LocalDateTime effStop = stop != null ? stop : deal.getStopTime();
        LitemallGoods goods = goodsService.findById(deal.getGoodsId());
        Result floorError = validateCjFloor(goods, effPrice);
        if (floorError != null) {
            return floorError;
        }
        BigDecimal referenceRetail = live && deal.getOriginalRetailPrice() != null
                ? deal.getOriginalRetailPrice()
                : goods != null ? goods.getRetailPrice() : null;
        String windowError = validateWindow(effPrice, referenceRetail, effStart, effStop);
        if (windowError != null) {
            return Result.fail(ERRNO_INVALID, windowError);
        }
        if (seckillService.hasOverlapping(deal.getGoodsId(), effStart, effStop, id)
                && (status == null || status == 1)) {
            return Result.fail(ERRNO_CONFLICT, "an enabled deal already overlaps this window for goods " + deal.getGoodsId());
        }
        LitemallSeckill patch = new LitemallSeckill();
        patch.setId(id);
        patch.setPrice(dealPrice);
        patch.setStartTime(start);
        patch.setStopTime(stop);
        patch.setStock(stock);
        patch.setStatus(status);
        seckillService.updateById(patch);
        return Result.ok(seckillService.findById(id));
    }

    /** Logical delete; a live swap is unwound by the scheduler's next tick (expiry query ignores deleted). */
    public Result delete(Integer id) {
        LitemallSeckill deal = seckillService.findById(id);
        if (deal == null) {
            return Result.fail(ERRNO_INVALID, "deal " + id + " not found");
        }
        seckillService.deleteById(id);
        return Result.ok(deal);
    }

    public Map<String, Object> adminList(int page, int limit) {
        int offset = Math.max(0, (page - 1) * limit);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LitemallSeckill deal : seckillService.queryAdminPage(offset, limit)) {
            rows.add(toAdminView(deal));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", seckillService.countAdmin());
        data.put("page", page);
        data.put("limit", limit);
        data.put("list", rows);
        return data;
    }

    public LitemallSeckill findById(Integer id) {
        return seckillService.findById(id);
    }

    /** Customer deal block for a goods, or null when no live deal (drives detail-page countdown). */
    public Map<String, Object> liveDealBlock(Integer goodsId) {
        LitemallSeckill deal = seckillService.findLiveByGoodsId(goodsId);
        if (deal == null) {
            return null;
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("dealPrice", deal.getPrice());
        block.put("originalPrice", deal.getOriginalRetailPrice());
        block.put("endEpoch", DealMath.toEpochMilli(deal.getStopTime()));
        block.put("stock", deal.getStock());
        block.put("claimed", deal.getSales());
        block.put("claimedPct", DealMath.claimedPct(deal));
        return block;
    }

    public Map<String, Object> toAdminView(LitemallSeckill deal) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", deal.getId());
        view.put("goodsId", deal.getGoodsId());
        view.put("goodsName", deal.getGoodsName());
        view.put("picUrl", deal.getPicUrl());
        view.put("dealPrice", deal.getPrice());
        view.put("originalRetailPrice", deal.getOriginalRetailPrice());
        view.put("stock", deal.getStock());
        view.put("sales", deal.getSales());
        view.put("startTime", deal.getStartTime());
        view.put("stopTime", deal.getStopTime());
        view.put("enabled", deal.getStatus() != null && deal.getStatus() == 1);
        view.put("live", Boolean.TRUE.equals(deal.getPriceSwapped()));
        return view;
    }

    /**
     * Wave-12 CJ floor: the deal price may never go below the REAL captured wholesale cost
     * ({@code litemall_goods.cost}, USD basis since the ×1.25 repricing). No captured cost
     * (0.00 = the V2 column default, row not re-synced since V45) ⇒ the cost basis is
     * unknowable and the deal is refused outright (652) — never a guessed floor.
     * Returns null when the goods is not CJ-sourced or the floor holds.
     */
    private Result validateCjFloor(LitemallGoods goods, BigDecimal dealPrice) {
        if (goods == null || !"cj".equalsIgnoreCase(goods.getSource()) || dealPrice == null) {
            return null;
        }
        BigDecimal cost = goods.getCost();
        if (cost == null || cost.signum() <= 0) {
            return Result.fail(ERRNO_CJ, "goods " + goods.getId()
                    + " has no captured CJ cost — cost basis unknown, deal refused");
        }
        if (dealPrice.compareTo(cost) < 0) {
            return Result.fail(ERRNO_INVALID, "dealPrice " + dealPrice
                    + " is below the CJ cost floor " + cost + " (captured wholesale cost)");
        }
        return null;
    }

    private static String validateWindow(BigDecimal dealPrice, BigDecimal retail,
                                         LocalDateTime start, LocalDateTime stop) {
        if (dealPrice == null || dealPrice.signum() <= 0) {
            return "dealPrice must be positive";
        }
        if (retail != null && dealPrice.compareTo(retail) >= 0) {
            return "dealPrice " + dealPrice + " must be below the goods retail price " + retail;
        }
        if (start == null || stop == null || !stop.isAfter(start)) {
            return "window requires start < stop";
        }
        if (stop.isBefore(LocalDateTime.now())) {
            return "window is entirely in the past";
        }
        return null;
    }
}
