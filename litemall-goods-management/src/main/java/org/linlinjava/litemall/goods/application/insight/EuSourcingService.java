package org.linlinjava.litemall.goods.application.insight;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.springframework.stereotype.Service;

/**
 * Wave 26 Phase 1b: how much of OUR on-sale catalogue actually holds EU (German) warehouse stock.
 *
 * <p>Distinct from the Phase-1a probe, which measured CJ SUPPLY — what we could acquire. This
 * measures what we HOLD, from the per-country inventory split captured at the enrichment seam.
 *
 * <p><b>The denominator is the whole point.</b> Coverage grows with the enrichment rotation, so a
 * survival percentage over {@code onSaleCount} would read as "3% of our catalogue has EU stock"
 * when the truth is "we have only probed 3% of it". {@code euSurvivalPct} is therefore computed
 * over {@code probedCount}, {@code probedCount} ships in every row, and both are {@code null} —
 * never 0 — where nothing has been probed yet. Absent is not zero.
 */
@Service
public class EuSourcingService {

    private final LitemallCjProductService cjProductStore;
    private final CJDropshippingConfig config;

    public EuSourcingService(LitemallCjProductService cjProductStore, CJDropshippingConfig config) {
        this.cjProductStore = cjProductStore;
        this.config = config;
    }

    public Map<String, Object> report() {
        List<Map<String, Object>> categories = new ArrayList<>();
        long onSaleTotal = 0;
        long probedTotal = 0;
        long euTotal = 0;

        for (Map<String, Object> row : cjProductStore.euSurvivalByRoot()) {
            long onSale = asLong(row.get("onSaleCount"));
            long probed = asLong(row.get("probedCount"));
            long euStocked = asLong(row.get("euStockedCount"));
            onSaleTotal += onSale;
            probedTotal += probed;
            euTotal += euStocked;

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("categoryId", asInt(row.get("categoryId")));
            out.put("name", row.get("name"));
            out.put("onSaleCount", onSale);
            out.put("probedCount", probed);
            out.put("euStockedCount", probed == 0 ? null : euStocked);
            // null, not 0: "we have not looked" and "we looked and found none" are different answers
            // and this endpoint exists precisely to keep them apart.
            out.put("euSurvivalPct", probed == 0 ? null : round2(euStocked * 100.0 / probed));
            out.put("avgEuStock", row.get("avgEuStock"));
            categories.add(out);
        }

        categories.sort((a, b) -> {
            Double pa = (Double) a.get("euSurvivalPct");
            Double pb = (Double) b.get("euSurvivalPct");
            if (pa == null && pb == null) {
                return Long.compare(asLong(b.get("onSaleCount")), asLong(a.get("onSaleCount")));
            }
            if (pa == null) {
                return 1; // unprobed categories sort last — they are unknowns, not zeros
            }
            if (pb == null) {
                return -1;
            }
            return Double.compare(pb, pa);
        });

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("onSaleCount", onSaleTotal);
        totals.put("probedCount", probedTotal);
        totals.put("euStockedCount", probedTotal == 0 ? null : euTotal);
        totals.put("euSurvivalPct", probedTotal == 0 ? null : round2(euTotal * 100.0 / probedTotal));
        totals.put("coveragePct", onSaleTotal == 0 ? null : round2(probedTotal * 100.0 / onSaleTotal));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("countries", new ArrayList<>(config.getEuWarehouseCountries()));
        out.put("categories", categories);
        out.put("totals", totals);
        return out;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static long asLong(Object v) {
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }

    private static Integer asInt(Object v) {
        return v instanceof Number ? ((Number) v).intValue() : null;
    }
}
