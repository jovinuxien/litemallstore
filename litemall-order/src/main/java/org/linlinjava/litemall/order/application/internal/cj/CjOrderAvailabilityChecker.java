package org.linlinjava.litemall.order.application.internal.cj;

import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException;
import org.linlinjava.litemall.order.application.util.exception.order.LitemallOrderServiceException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjStockFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallGoodsFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Submit-time gate that a CJ order can actually be fulfilled BEFORE it is placed. CJ
 * fulfillment happens at pay time ({@link CjFulfillmentService}) and needs a real
 * {@code cj_vid} for every line ({@link CjOrderLineResolver#resolveVid(Integer)}); a
 * shallow catalog-fill row that was marked on-sale before enrichment carries no
 * {@code cj_vid}, so the pay step would throw and roll the payment back.
 *
 * <p>Rather than let the customer discover that only at payment, the orchestrator runs
 * this pre-check right after the order source resolves to {@code 'cj'} (outside the
 * placement transaction, mirroring the empty-cart / mixed-cart guards). Any unresolvable
 * line raises {@link LitemallOrderServiceException} — mapped to a clean {@code 422}
 * submit-failed envelope — carrying a customer-facing message (never the internal
 * "no cj_vid on the row" text). The pay-time resolution stays as defence-in-depth.
 *
 * <p>Lines that DO resolve get a live CJ stock check ({@link CjStockFacade},
 * {@code product/stock/queryByVid}): a requested quantity exceeding what CJ positively
 * reports available raises the same 422, naming the offending line(s). The stock check is
 * ADVISORY — an unknown answer (CJ down, breaker open, no warehouse rows) logs and passes,
 * because the vid guard above already gates the hard failures and pay-time placement is
 * the final arbiter. Degrade decision documented in docs/adr-cj-lifecycle-parity.md.
 */
@Service
public class CjOrderAvailabilityChecker {

    private static final Logger log = LoggerFactory.getLogger(CjOrderAvailabilityChecker.class);

    private final CjOrderLineResolver lineResolver;
    private final CjStockFacade stockFacade;
    private final LitemallGoodsFacade goodsFacade;

    public CjOrderAvailabilityChecker(CjOrderLineResolver lineResolver, CjStockFacade stockFacade,
                                      LitemallGoodsFacade goodsFacade) {
        this.lineResolver = lineResolver;
        this.stockFacade = stockFacade;
        this.goodsFacade = goodsFacade;
    }

    /**
     * Assert every CJ cart line resolves to a CJ variant id AND (advisorily) that CJ has the
     * requested quantity in stock, aggregating ALL offending lines so the customer sees every
     * affected item at once. Throws {@link LitemallOrderServiceException} (→ 422) if any line
     * cannot be fulfilled at CJ.
     */
    public void assertAllFulfillable(List<LitemallCartAggregate> cartList) {
        if (cartList == null) {
            return;
        }
        List<String> unavailable = new ArrayList<>();
        // vid → aggregated requested qty / display label, in cart order (same variant may
        // reach checkout on more than one line; CJ stock is per variant, so compare the sum).
        Map<String, Integer> requestedByVid = new LinkedHashMap<>();
        Map<String, String> labelByVid = new LinkedHashMap<>();
        for (LitemallCartAggregate line : cartList) {
            if (line == null || line.getProductId() == null) {
                continue;
            }
            String label = StringUtils.hasText(line.getGoodsName())
                    ? line.getGoodsName()
                    : "product " + line.getProductId().getId();
            try {
                String vid = lineResolver.resolveVid(line.getProductId().getId());
                int quantity = line.getNumber() != null && line.getNumber() > 0 ? line.getNumber() : 1;
                requestedByVid.merge(vid, quantity, Integer::sum);
                labelByVid.putIfAbsent(vid, label);
            } catch (LitemallCjOrderException ex) {
                unavailable.add(label);
                log.warn("CJ submit blocked: line productId={} unfulfillable at CJ: {}",
                        line.getProductId().getId(), ex.getMessage());
                // Demand-Driven CJ Enrichment (part 2): a blocked line is the loudest possible
                // demand signal. Re-reading the goods through the facade fires goods-management's
                // /srv/goods/goodsdetail on-demand enrichment hook, so the customer's retry
                // ("try again shortly", below) finds real variants. Fire-and-forget — the 422
                // response never waits on it and a goods-management outage changes nothing.
                triggerOnDemandEnrichment(line);
            }
        }
        if (!unavailable.isEmpty()) {
            throw new LitemallOrderServiceException(
                    "Some items are temporarily unavailable for checkout while we finish syncing "
                            + "their supplier details: " + String.join(", ", unavailable)
                            + ". We are fetching those details now — please try again in about a minute,"
                            + " or remove them from your cart.");
        }

        List<String> overStock = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : requestedByVid.entrySet()) {
            String vid = entry.getKey();
            int requested = entry.getValue();
            Optional<Integer> available = stockFacade.availableStock(vid);
            if (available.isEmpty()) {
                log.warn("CJ stock unknown for vid {} at submit; proceeding (advisory check)", vid);
                continue;
            }
            if (requested > available.get()) {
                overStock.add(labelByVid.get(vid)
                        + " (requested " + requested + ", only " + available.get() + " available)");
                log.warn("CJ submit blocked: vid {} requested {} exceeds CJ stock {}",
                        vid, requested, available.get());
            }
        }
        if (!overStock.isEmpty()) {
            throw new LitemallOrderServiceException(
                    "Some items exceed the supplier's currently available stock: "
                            + String.join(", ", overStock)
                            + ". Please lower the quantity or remove them from your cart.");
        }
    }

    /**
     * Fire-and-forget goods re-read whose only purpose is to trip goods-management's
     * on-demand enrichment hook on {@code /srv/goods/goodsdetail} for a shallow CJ goods.
     * Never throws, never blocks the 422 path; the facade's own timeouts/breaker apply.
     */
    private void triggerOnDemandEnrichment(LitemallCartAggregate line) {
        if (line.getGoodsId() == null) {
            return;
        }
        int goodsId = line.getGoodsId().getId();
        CompletableFuture.runAsync(() -> {
            try {
                goodsFacade.batchGetGoods(Set.of(goodsId));
            } catch (RuntimeException ex) {
                log.debug("on-demand enrichment trigger for goods {} failed: {}", goodsId, ex.getMessage());
            }
        });
    }
}
