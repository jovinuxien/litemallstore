package org.linlinjava.litemall.order.application.internal.cj;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjDropshipOrderFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjLogisticsOption;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderPlacement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Checkout-time CJ logistics quote: resolves each line's {@code cj_vid} (as placement does) and asks
 * the facade for the same freightCalculate-selected line that {@code placeForPaidOrder} will use.
 * Quotes are cached (Caffeine, 15 min, negative results included) because CJ enforces ~1 QPS
 * account-wide and a checkout page can re-render often; the REAL line is still re-resolved live at
 * pay time and persisted on the order, so a stale quote only ever mislabels an *estimate*.
 */
@Service
public class CjFreightQuoteService {

    private static final Logger log = LoggerFactory.getLogger(CjFreightQuoteService.class);

    /** One quoted cart line: the native productId (as checkout knows it) and quantity. */
    public record QuoteItem(Integer productId, Integer quantity) {}

    private final CjDropshipOrderFacade cjOrderFacade;
    private final CjOrderLineResolver lineResolver;
    private final Cache<String, List<CjLogisticsOption>> cache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(15))
            .build();

    public CjFreightQuoteService(CjDropshipOrderFacade cjOrderFacade, CjOrderLineResolver lineResolver) {
        this.cjOrderFacade = cjOrderFacade;
        this.lineResolver = lineResolver;
    }

    /**
     * All CJ logistics lines offered for a destination country + cart items — the
     * delivery-option chooser's data. One cached freightCalculate call feeds both this
     * and {@link #quote}. Never throws: an unresolvable line, CJ outage, or no-offer
     * lane returns an empty list.
     */
    public List<CjLogisticsOption> options(String countryCode, List<QuoteItem> items) {
        if (countryCode == null || countryCode.isBlank() || items == null || items.isEmpty()) {
            return List.of();
        }
        List<CjOrderPlacement.Line> lines;
        try {
            lines = resolveLines(items);
        } catch (LitemallCjOrderException e) {
            log.warn("CJ freight quote skipped — line resolution failed: {}", e.getMessage());
            return List.of();
        }
        if (lines.isEmpty()) {
            return List.of();
        }
        String key = countryCode.trim().toUpperCase() + "|" + lines.stream()
                .map(l -> l.getVid() + ":" + l.getQuantity())
                .sorted()
                .collect(Collectors.joining(","));
        // Caffeine serializes concurrent loads per key; negative results are cached too so a lane
        // CJ offers nothing for doesn't hammer the quota on every checkout re-render.
        return cache.get(key, k -> cjOrderFacade.quoteLogisticsOptions(countryCode.trim().toUpperCase(), lines));
    }

    /**
     * The line placement would use today (default-else-cheapest — same rule as pay time
     * with no customer preference). Kept as the quote's headline "ships via" estimate.
     */
    public CjLogisticsOption quote(String countryCode, List<QuoteItem> items) {
        return cjOrderFacade.chooseLogistics(options(countryCode, items), null);
    }

    private List<CjOrderPlacement.Line> resolveLines(List<QuoteItem> items) {
        List<CjOrderPlacement.Line> lines = new ArrayList<>(items.size());
        for (QuoteItem item : items) {
            if (item == null || item.productId() == null) {
                continue;
            }
            String vid = lineResolver.resolveVid(item.productId());
            int quantity = item.quantity() != null && item.quantity() > 0 ? item.quantity() : 1;
            lines.add(CjOrderPlacement.Line.builder().vid(vid).quantity(quantity).build());
        }
        return lines;
    }
}
