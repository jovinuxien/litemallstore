package org.linlinjava.litemall.goods.application.deals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.db.domain.LitemallSeckill;
import org.linlinjava.litemall.db.service.LitemallGoodsProductService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.linlinjava.litemall.db.service.LitemallSeckillService;
import org.linlinjava.litemall.goods.application.search.CjProductPromotionService;
import org.linlinjava.litemall.goods.application.search.SearchReindexService;
import org.linlinjava.litemall.goods.domain.deals.DealMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ONLY writer of the price-swap lifecycle ({@code price_swapped},
 * {@code original_retail_price}) — ADR {@code docs/adr-flash-deals-price-swap.md}.
 * Each tick, in order:
 *
 * <ol>
 *   <li><b>Activate</b> enabled deals whose window opened: capture the goods'
 *       retail price, swap the deal price in (lifting {@code counter_price} to
 *       the pre-deal retail when the goods carried no strike-through), swap
 *       EVERY SKU row proportionally (checkout money reads
 *       {@code litemall_goods_product.price} — V40 records {o,s} per SKU),
 *       mark {@code price_swapped=1}, reindex — the Phase-A discount signals
 *       (discount_pct / deal_flag) then light up through the normal indexer.</li>
 *   <li><b>Expire/unwind</b> swapped deals whose window closed or that were
 *       disabled/deleted: restore the captured retail + per-SKU prices (each
 *       skipped with a WARN if an admin hand-changed it mid-deal), mark swap
 *       off, reindex.</li>
 *   <li><b>Refresh claimed</b> on live capped deals from paid order lines in
 *       the window; a sold-out cap unwinds early exactly like an expiry.</li>
 *   <li><b>Re-score urgency</b>: reindex a live deal when its computed urgency
 *       (0–100, gaussian in hours-to-end) drifts ≥2 from the last indexed
 *       value — index-time urgency instead of a query-time decay because OCS
 *       {@code SCRIPT_CODE} scoring takes no params (spike 2026-07-15).</li>
 * </ol>
 *
 * Kill switch {@code litemall.deals.lifecycle-enabled} (default true);
 * cadence {@code litemall.deals.tick-ms} (default 60s).
 */
@Component
public class FlashDealLifecycleTask {

    private static final Logger log = LoggerFactory.getLogger(FlashDealLifecycleTask.class);

    private static final BigDecimal MIN_SKU_PRICE = new BigDecimal("0.01");

    private final LitemallSeckillService seckillService;
    private final LitemallGoodsService goodsService;
    private final LitemallGoodsProductService productService;
    private final SearchReindexService reindexService;
    private final LitemallCjProductService cjProductService;
    private final CjProductPromotionService cjPromotionService;
    private final ObjectMapper json = new ObjectMapper();
    /** Last urgency pushed to the index per deal id (instance-scoped; a restart just re-pushes once). */
    private final Map<Integer, Integer> lastIndexedUrgency = new ConcurrentHashMap<>();

    @Value("${litemall.deals.lifecycle-enabled:true}")
    private boolean enabled;

    public FlashDealLifecycleTask(LitemallSeckillService seckillService,
                                  LitemallGoodsService goodsService,
                                  LitemallGoodsProductService productService,
                                  SearchReindexService reindexService,
                                  LitemallCjProductService cjProductService,
                                  CjProductPromotionService cjPromotionService) {
        this.seckillService = seckillService;
        this.goodsService = goodsService;
        this.productService = productService;
        this.reindexService = reindexService;
        this.cjProductService = cjProductService;
        this.cjPromotionService = cjPromotionService;
    }

    @Scheduled(fixedDelayString = "${litemall.deals.tick-ms:60000}")
    public void tick() {
        if (!enabled) {
            return;
        }
        try {
            activateDue();
            expireDue();
            refreshLiveDeals();
        } catch (Exception e) {
            // Never let one bad tick kill the schedule.
            log.error("flash-deal lifecycle tick failed", e);
        }
    }

    private void activateDue() {
        for (LitemallSeckill deal : seckillService.queryDueForActivation()) {
            LitemallGoods goods = goodsService.findById(deal.getGoodsId());
            if (goods == null) {
                log.warn("deal {} references missing goods {} — disabling", deal.getId(), deal.getGoodsId());
                disable(deal);
                continue;
            }
            BigDecimal retail = goods.getRetailPrice();
            if (retail == null || deal.getPrice() == null || deal.getPrice().compareTo(retail) >= 0) {
                log.warn("deal {} price {} not below goods {} retail {} — disabling",
                        deal.getId(), deal.getPrice(), deal.getGoodsId(), retail);
                disable(deal);
                continue;
            }
            LitemallGoods goodsPatch = new LitemallGoods();
            goodsPatch.setId(goods.getId());
            goodsPatch.setRetailPrice(deal.getPrice());
            if (goods.getCounterPrice() == null || goods.getCounterPrice().compareTo(retail) < 0) {
                // No (or weaker) strike-through before the deal: the pre-deal retail becomes the
                // anchor price. On unwind retail returns to the same value, so the goods row ends
                // byte-identical for the common counter==retail case.
                goodsPatch.setCounterPrice(retail);
            }
            goodsService.updateById(goodsPatch);

            // Checkout money reads litemall_goods_product.price (cart-add snapshots it, submit
            // re-stamps it) — the goods-row swap above is display-only. Swap every SKU
            // proportionally so the charge matches the advertised deal; capture {o,s} per SKU
            // for the per-SKU admin-wins restore at unwind (V40).
            BigDecimal factor = deal.getPrice().divide(retail, 8, RoundingMode.HALF_UP);
            Map<String, Map<String, BigDecimal>> skuCapture = new LinkedHashMap<>();
            for (LitemallGoodsProduct sku : productService.queryByGid(goods.getId())) {
                if (sku.getPrice() == null) {
                    continue;
                }
                // The base SKU (price == pre-deal retail) lands EXACTLY on the deal price;
                // other SKUs get the same proportional cut, floored at one cent.
                BigDecimal swapped = sku.getPrice().compareTo(retail) == 0
                        ? deal.getPrice()
                        : sku.getPrice().multiply(factor).setScale(2, RoundingMode.HALF_UP).max(MIN_SKU_PRICE);
                Map<String, BigDecimal> entry = new LinkedHashMap<>();
                entry.put("o", sku.getPrice());
                entry.put("s", swapped);
                skuCapture.put(String.valueOf(sku.getId()), entry);
                LitemallGoodsProduct skuPatch = new LitemallGoodsProduct();
                skuPatch.setId(sku.getId());
                skuPatch.setPrice(swapped);
                productService.updateById(skuPatch);
            }

            LitemallSeckill patch = new LitemallSeckill();
            patch.setId(deal.getId());
            patch.setOriginalRetailPrice(retail);
            patch.setPriceSwapped(true);
            patch.setOriginalSkuPrices(toJson(skuCapture));
            seckillService.updateById(patch);
            reindexService.reindexGoods(goods.getId());
            log.info("deal {} LIVE: goods {} retail {} -> {} until {}",
                    deal.getId(), deal.getGoodsId(), retail, deal.getPrice(), deal.getStopTime());
        }
    }

    private void expireDue() {
        for (LitemallSeckill deal : seckillService.queryDueForExpiry()) {
            unwind(deal, "window closed / disabled");
        }
    }

    private void refreshLiveDeals() {
        for (LitemallSeckill deal : seckillService.querySwapped()) {
            boolean reindex = false;
            // Claimed = paid order lines inside the window (order integration is read-only —
            // the swap already made every checkout charge the deal price).
            int claimed = seckillService.sumPaidQuantityInWindow(
                    deal.getGoodsId(), deal.getStartTime(), deal.getStopTime());
            if (!Objects.equals(claimed, deal.getSales())) {
                LitemallSeckill patch = new LitemallSeckill();
                patch.setId(deal.getId());
                patch.setSales(claimed);
                seckillService.updateById(patch);
                deal.setSales(claimed);
                reindex = true;
            }
            if (deal.getStock() != null && deal.getStock() > 0 && claimed >= deal.getStock()) {
                unwind(deal, "cap of " + deal.getStock() + " sold out");
                continue;
            }
            int urgency = DealMath.urgencyOf(deal.getStopTime());
            Integer last = lastIndexedUrgency.get(deal.getId());
            if (last == null || Math.abs(urgency - last) >= 2) {
                reindex = true;
            }
            if (reindex) {
                lastIndexedUrgency.put(deal.getId(), urgency);
                reindexService.reindexGoods(deal.getGoodsId());
            }
        }
    }

    private void unwind(LitemallSeckill deal, String reason) {
        LitemallGoods goods = goodsService.findById(deal.getGoodsId());
        if (goods != null && deal.getOriginalRetailPrice() != null) {
            if (goods.getRetailPrice() != null && goods.getRetailPrice().compareTo(deal.getPrice()) == 0) {
                LitemallGoods goodsPatch = new LitemallGoods();
                goodsPatch.setId(goods.getId());
                goodsPatch.setRetailPrice(deal.getOriginalRetailPrice());
                goodsService.updateById(goodsPatch);
            } else {
                log.warn("deal {} unwind: goods {} retail {} no longer equals deal price {} — leaving it (admin edit wins)",
                        deal.getId(), deal.getGoodsId(), goods.getRetailPrice(), deal.getPrice());
            }
        }
        restoreSkuPrices(deal);
        LitemallSeckill patch = new LitemallSeckill();
        patch.setId(deal.getId());
        patch.setPriceSwapped(false);
        seckillService.updateById(patch);
        lastIndexedUrgency.remove(deal.getId());
        // Wave 12 (unparked a82a19e0e): CJ goods re-promote from the snapshot right after the
        // swap clears — the promote path withheld price writes while the deal was live, so a
        // mid-deal CJ reprice converges NOW rather than waiting for the nightly batch.
        if (goods != null && "cj".equalsIgnoreCase(goods.getSource()) && goods.getCjPid() != null) {
            try {
                LitemallCjProduct snapshot = cjProductService.findByPid(goods.getCjPid());
                if (snapshot != null) {
                    cjPromotionService.promote(snapshot);
                }
            } catch (RuntimeException e) {
                log.warn("deal {} unwind: CJ re-promote of pid {} failed — nightly promote will converge: {}",
                        deal.getId(), goods.getCjPid(), e.getMessage());
            }
        }
        if (goods != null) {
            reindexService.reindexGoods(goods.getId());
        }
        log.info("deal {} unwound ({}): goods {} retail restored to {}",
                deal.getId(), reason, deal.getGoodsId(), deal.getOriginalRetailPrice());
    }

    /** Restore each swapped SKU price, per-SKU admin-wins: only rows still at the swapped value revert. */
    private void restoreSkuPrices(LitemallSeckill deal) {
        if (deal.getOriginalSkuPrices() == null || deal.getOriginalSkuPrices().isEmpty()) {
            return;
        }
        Map<String, Map<String, BigDecimal>> capture;
        try {
            capture = json.readValue(deal.getOriginalSkuPrices(),
                    new TypeReference<Map<String, Map<String, BigDecimal>>>() { });
        } catch (Exception e) {
            log.error("deal {} unwind: unparseable original_sku_prices — SKU prices NOT restored: {}",
                    deal.getId(), deal.getOriginalSkuPrices(), e);
            return;
        }
        for (Map.Entry<String, Map<String, BigDecimal>> entry : capture.entrySet()) {
            Integer skuId = Integer.valueOf(entry.getKey());
            BigDecimal original = entry.getValue().get("o");
            BigDecimal swapped = entry.getValue().get("s");
            LitemallGoodsProduct current = productService.findById(skuId);
            if (current == null || original == null || swapped == null) {
                continue;
            }
            if (current.getPrice() != null && current.getPrice().compareTo(swapped) == 0) {
                LitemallGoodsProduct skuPatch = new LitemallGoodsProduct();
                skuPatch.setId(skuId);
                skuPatch.setPrice(original);
                productService.updateById(skuPatch);
            } else {
                log.warn("deal {} unwind: SKU {} price {} no longer equals swapped {} — leaving it (admin edit wins)",
                        deal.getId(), skuId, current.getPrice(), swapped);
            }
        }
    }

    private String toJson(Map<String, Map<String, BigDecimal>> capture) {
        try {
            return json.writeValueAsString(capture);
        } catch (Exception e) {
            // Never block activation on serialization; unwind falls back to the goods-level restore.
            log.error("flash-deal SKU capture serialization failed", e);
            return "";
        }
    }

    private void disable(LitemallSeckill deal) {
        LitemallSeckill patch = new LitemallSeckill();
        patch.setId(deal.getId());
        patch.setStatus((byte) 0);
        seckillService.updateById(patch);
    }
}
