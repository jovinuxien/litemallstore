package org.linlinjava.litemall.order.application.internal.cj;

import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException;
import org.linlinjava.litemall.order.application.util.exception.order.LitemallOrderServiceException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
 */
@Service
public class CjOrderAvailabilityChecker {

    private static final Logger log = LoggerFactory.getLogger(CjOrderAvailabilityChecker.class);

    private final CjOrderLineResolver lineResolver;

    public CjOrderAvailabilityChecker(CjOrderLineResolver lineResolver) {
        this.lineResolver = lineResolver;
    }

    /**
     * Assert every CJ cart line resolves to a CJ variant id, aggregating ALL unfulfillable
     * lines so the customer sees every affected item at once. Throws
     * {@link LitemallOrderServiceException} (→ 422) if any line cannot be fulfilled at CJ.
     */
    public void assertAllFulfillable(List<LitemallCartAggregate> cartList) {
        if (cartList == null) {
            return;
        }
        List<String> unavailable = new ArrayList<>();
        for (LitemallCartAggregate line : cartList) {
            if (line == null || line.getProductId() == null) {
                continue;
            }
            try {
                lineResolver.resolveVid(line.getProductId().getId());
            } catch (LitemallCjOrderException ex) {
                String label = StringUtils.hasText(line.getGoodsName())
                        ? line.getGoodsName()
                        : "product " + line.getProductId().getId();
                unavailable.add(label);
                log.warn("CJ submit blocked: line productId={} unfulfillable at CJ: {}",
                        line.getProductId().getId(), ex.getMessage());
            }
        }
        if (!unavailable.isEmpty()) {
            throw new LitemallOrderServiceException(
                    "Some items are temporarily unavailable for checkout while we finish syncing "
                            + "their supplier details: " + String.join(", ", unavailable)
                            + ". Please try again shortly or remove them from your cart.");
        }
    }
}
