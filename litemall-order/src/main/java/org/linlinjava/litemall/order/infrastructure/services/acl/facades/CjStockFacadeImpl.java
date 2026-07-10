package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.linlinjava.litemall.order.infrastructure.services.cj.CjTokenService;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.CjStockFeignClient;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.stock.CjStockQueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * {@link CjStockFacade} implementation over {@code product/stock/queryByVid}. Answers are cached
 * for 60s per vid (Caffeine, negative/unknown results included) so a multi-line cart or a quick
 * submit retry doesn't hammer CJ's ~1 QPS account-wide quota; the check is advisory, so a
 * marginally stale figure is acceptable.
 *
 * <p>Warehouse selection: an order ships from ONE warehouse, so the availability that matters is
 * the single-warehouse figure — prefer the configured ship-from country's warehouse (what
 * placement uses), else the best-stocked warehouse anywhere. An empty warehouse list is treated
 * as UNKNOWN, not zero: factory-fulfilled listings can legitimately carry no warehouse rows, and
 * a false 422 is worse than a pass for an advisory gate (the pay-time CJ placement remains the
 * arbiter).
 */
@Component
public class CjStockFacadeImpl implements CjStockFacade {

    private static final Logger log = LoggerFactory.getLogger(CjStockFacadeImpl.class);

    private final CjStockFeignClient stockFeignClient;
    private final CjTokenService cjTokenService;
    /** CJ ship-from warehouse country — the warehouse whose stock placement will draw on. */
    private final String fromCountryCode;
    private final Cache<String, Optional<Integer>> cache = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(Duration.ofSeconds(60))
            .build();

    public CjStockFacadeImpl(CjStockFeignClient stockFeignClient, CjTokenService cjTokenService,
                             @Value("${spring.cjdropship.api.from-country-code:CN}") String fromCountryCode) {
        this.stockFeignClient = stockFeignClient;
        this.cjTokenService = cjTokenService;
        this.fromCountryCode = fromCountryCode;
    }

    @Override
    public Optional<Integer> availableStock(String vid) {
        if (vid == null || vid.isBlank()) {
            return Optional.empty();
        }
        return cache.get(vid.trim(), this::query);
    }

    private Optional<Integer> query(String vid) {
        try {
            String token = cjTokenService.getValidToken();
            CjStockQueryResponse response = stockFeignClient.queryByVid(token, vid);
            boolean usable = response != null && (response.isResult() || response.getCode() == 200);
            if (!usable) {
                log.warn("CJ stock query for vid {} gave no usable answer ({}); treating as unknown",
                        vid, response == null ? "null response" : response.getMessage());
                return Optional.empty();
            }
            List<CjStockQueryResponse.WarehouseStock> warehouses = response.getData();
            if (warehouses == null || warehouses.isEmpty()) {
                log.info("CJ stock query for vid {} returned no warehouse rows; treating as unknown "
                        + "(factory-fulfilled listings carry none)", vid);
                return Optional.empty();
            }
            return Optional.of(pickWarehouseStock(warehouses));
        } catch (RuntimeException e) {
            log.warn("CJ stock query failed for vid {}; treating as unknown: {}", vid, e.getMessage());
            return Optional.empty();
        }
    }

    private int pickWarehouseStock(List<CjStockQueryResponse.WarehouseStock> warehouses) {
        int bestAnywhere = 0;
        Integer bestFromCountry = null;
        for (CjStockQueryResponse.WarehouseStock w : warehouses) {
            if (w == null) {
                continue;
            }
            int total = warehouseTotal(w);
            bestAnywhere = Math.max(bestAnywhere, total);
            if (fromCountryCode.equalsIgnoreCase(w.getCountryCode())) {
                bestFromCountry = bestFromCountry == null ? total : Math.max(bestFromCountry, total);
            }
        }
        return bestFromCountry != null ? bestFromCountry : bestAnywhere;
    }

    private static int warehouseTotal(CjStockQueryResponse.WarehouseStock w) {
        if (w.getTotalInventoryNum() != null) {
            return w.getTotalInventoryNum();
        }
        int cj = w.getCjInventoryNum() != null ? w.getCjInventoryNum() : 0;
        int factory = w.getFactoryInventoryNum() != null ? w.getFactoryInventoryNum() : 0;
        return cj + factory;
    }
}
