package org.linlinjava.litemall.goods.application.goods;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Wave 28 §3.1 — the ONE place that turns a CJ warehouse measurement into a customer-facing
 * claim. Both surfaces that make that claim read it: the PDP's {@code euStock} key (Wave 26
 * Phase 1b) and the checkout's batched {@code GET /srv/goods/origin} (this wave). Keeping them
 * on a single predicate is the point — a product the PDP badges as "in stock in Germany" and the
 * checkout labels as shipping from China would be two honest surfaces telling one lie.
 *
 * <p><b>What a reading means.</b> Three states of {@code litemall_cj_product.eu_stock_num}:
 * <ul>
 *   <li>NULL — never probed. We do not know, so we claim nothing.</li>
 *   <li>0 — probed, no EU stock. Nothing to claim either.</li>
 *   <li>&gt; 0 — the LAST inventory probe found units in an EU warehouse. Only then does a
 *       product get a reading, and an origin only if that reading names a configured EU
 *       warehouse country ({@code spring.cjdropship.eu-warehouse-countries}, DE today — CJ's
 *       only EU warehouse, measured Wave 26 Phase 1a).</li>
 * </ul>
 * NULL and 0 collapse to "absent" here deliberately: the two are indistinguishable to a
 * customer-facing claim (the {@code eu_flag} rationale). Absence is the answer, never a
 * {@code "CN"} standing in for "we think China" and never a null — the storefront renders no
 * note for an absent row, which is the honest render.
 *
 * <p>A reading is a measurement with a timestamp, not a delivery promise. CJ still picks the
 * fulfilling warehouse at order time, so copy built on this must say "at last check".
 *
 * <p>Reads are the two the PDP has always done (goods row, then CJ snapshot by pid) — no new
 * shared-module mapper. The batch caller is a checkout cart, capped at {@link #MAX_IDS}, so
 * per-id reads are the right trade against a litemall-db change and its restart-every-dependent
 * discipline. Any failure on one id drops THAT row only: an origin note must never be able to
 * break a checkout, and one bad row must not hide the others.
 */
@Service
public class WarehouseOriginService {

    private static final Logger log = LoggerFactory.getLogger(WarehouseOriginService.class);

    /** Batch bound for the public read — a cart is a handful of lines, never hundreds. */
    public static final int MAX_IDS = 100;

    /** A measured, non-zero EU warehouse reading: units and the configured EU countries seen. */
    public record EuReading(int units, List<String> countries) {
    }

    /** One origin row for the checkout: the EU country the last probe found stock in. */
    public record Origin(int goodsId, String originCountry) {
    }

    private final LitemallGoodsService goodsRowService;
    private final LitemallCjProductService cjProductRowService;
    private final CJDropshippingConfig cjConfig;

    public WarehouseOriginService(LitemallGoodsService goodsRowService,
                                 LitemallCjProductService cjProductRowService,
                                 CJDropshippingConfig cjConfig) {
        this.goodsRowService = goodsRowService;
        this.cjProductRowService = cjProductRowService;
        this.cjConfig = cjConfig;
    }

    /**
     * Parses the {@code ids=1,2,3} query form: whitespace-tolerant, order-preserving, duplicates
     * and non-positive or non-numeric tokens dropped. A junk token is not the caller's error to
     * hear about — the checkout builds this list from its own cart ids, and a stray token would
     * only ever cost that one row.
     */
    public static List<Integer> parseIds(String raw) {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        for (String token : raw.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                int id = Integer.parseInt(t);
                if (id > 0) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
                // not an id — dropped, see above
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * The measured EU reading for a goods, or empty when there is nothing honest to claim:
     * unknown/deleted goods, local goods (no CJ pid), no snapshot, never probed, probed-zero.
     * Countries are filtered to the configured EU warehouse set so a badge only ever names a
     * real EU warehouse. Throws nothing on data it cannot read — callers decide the degrade.
     */
    public Optional<EuReading> measuredEuStock(Integer goodsId) {
        if (goodsId == null || goodsId <= 0) {
            return Optional.empty();
        }
        LitemallGoods goods = goodsRowService.findById(goodsId);
        if (goods == null || goods.getCjPid() == null || goods.getCjPid().isBlank()) {
            return Optional.empty();
        }
        LitemallCjProduct snapshot = cjProductRowService.findByPid(goods.getCjPid());
        if (snapshot == null || snapshot.getEuStockNum() == null || snapshot.getEuStockNum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new EuReading(snapshot.getEuStockNum(),
                euCountriesOf(snapshot.getWarehouseCountries())));
    }

    /**
     * Origin rows for a batch of goods ids — one row per goods that has a reading naming at least
     * one configured EU country; everything else is simply absent. A reading with units but no
     * recognisable EU country yields NO row (fail closed: we cannot name an origin we did not
     * measure). A lookup failure on one id is logged and skipped so the rest still answer.
     */
    public List<Origin> originsOf(Collection<Integer> goodsIds) {
        List<Origin> out = new ArrayList<>();
        if (goodsIds == null) {
            return out;
        }
        for (Integer id : new LinkedHashSet<>(goodsIds)) {
            try {
                measuredEuStock(id)
                        .filter(r -> !r.countries().isEmpty())
                        .ifPresent(r -> out.add(new Origin(id, r.countries().get(0))));
            } catch (RuntimeException ex) {
                log.debug("warehouse origin: goods {} skipped ({})", id, ex.getMessage());
            }
        }
        return out;
    }

    /** The configured EU countries actually seen for this product, so the badge names real ones. */
    List<String> euCountriesOf(String warehouseCountries) {
        List<String> seen = new ArrayList<>();
        if (warehouseCountries == null || warehouseCountries.isBlank()) {
            return seen;
        }
        for (String cc : warehouseCountries.split(",")) {
            String code = cc.trim().toUpperCase(Locale.ROOT);
            if (!code.isEmpty() && !seen.contains(code) && cjConfig.getEuWarehouseCountries().contains(code)) {
                seen.add(code);
            }
        }
        return seen;
    }
}
