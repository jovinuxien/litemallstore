package org.linlinjava.litemall.order.application.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.db.dao.FreightTemplateMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsMapper;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsExample;
import org.linlinjava.litemall.db.domain.LitemallShippingTemplate;
import org.linlinjava.litemall.db.domain.LitemallShippingTemplateFree;
import org.linlinjava.litemall.db.domain.LitemallShippingTemplateRegion;
import org.linlinjava.litemall.order.infrastructure.configuration.FreightTemplateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Single authority for freight pricing — BOTH the checkout quote endpoint and the
 * transactional submit path charge exactly what this service returns, so quote and
 * submit can never disagree (Wave 4, Task A — docs/adr-freight-templates.md).
 *
 * <p>Resolution ladder (outermost first):
 * <ol>
 *   <li><b>Feature flag / CJ bypass</b> — {@code freight.template.enabled=false} or a
 *       CJ-fulfilled cart short-circuits to the legacy flat rule (the informational CJ
 *       logistics quote block is untouched).</li>
 *   <li><b>Global free-shipping minimum</b> — subtotal ≥ {@code litemall_express_freight_min}
 *       ships free, template or not (pre-Wave-4 regression guarantee).</li>
 *   <li><b>Template groups</b> — cart lines group by the goods' {@code temp_id}
 *       (0 = unbound → the {@code is_default} template when one is set, else the flat
 *       bucket). Each group prices by the crmeb first/continue formula over the most
 *       specific matching region row: (country, province) → (country, NULL) → ('*').
 *       Templates with {@code appoint >= 1} evaluate free rules first (OR semantics:
 *       enough units OR enough amount → the group ships free). DOCUMENTED DEVIATION:
 *       a template group whose destination matches no region row flat-charges (crmeb
 *       ships it free). Piece-only v1: billing type 2/3 (weight/volume) is billed per
 *       piece with a breakdown note (columns are mapped; billing deferred by plan).</li>
 *   <li><b>Flat fallback</b> — the legacy {@code litemall_express_freight_value} for the
 *       unbound bucket, and for everything when templates are disabled.</li>
 * </ol>
 *
 * <p>Groups combine per {@code freight.template.combine-mode}: {@code max} (default) or
 * {@code sum} (crmeb-exact). Template data is Caffeine-cached for 5 minutes (admin CRUD
 * invalidates). This service NEVER throws for freight reasons — any resolution failure
 * degrades to the flat rule with {@code source=SYSTEM_FLAT} and a WARN.
 */
@Service
public class FreightCalculationService {

    private static final Logger log = LoggerFactory.getLogger(FreightCalculationService.class);

    public static final String SOURCE_TEMPLATE = "TEMPLATE";
    public static final String SOURCE_SYSTEM_FLAT = "SYSTEM_FLAT";
    public static final String SOURCE_FREE_MIN = "FREE_MIN";

    /** A cart line as freight sees it: goods + quantity (+ unit price for free-rule amounts). */
    public record FreightLine(Integer goodsId, int quantity, BigDecimal unitPrice) {
    }

    /** One priced group in the breakdown (template group or the flat bucket). */
    public static class BreakdownEntry {
        private final Integer templateId;      // null for the flat bucket / global rules
        private final String templateName;     // null for the flat bucket / global rules
        private final String source;           // TEMPLATE | SYSTEM_FLAT | FREE_MIN
        private final BigDecimal amount;
        private final String note;

        public BreakdownEntry(Integer templateId, String templateName, String source,
                              BigDecimal amount, String note) {
            this.templateId = templateId;
            this.templateName = templateName;
            this.source = source;
            this.amount = amount;
            this.note = note;
        }

        public Integer getTemplateId() {
            return templateId;
        }

        public String getTemplateName() {
            return templateName;
        }

        public String getSource() {
            return source;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public String getNote() {
            return note;
        }
    }

    /** The freight decision: the charged total, its overall source, and the per-group breakdown. */
    public static class FreightQuote {
        private final BigDecimal freight;
        private final String source;
        private final List<BreakdownEntry> breakdown;

        public FreightQuote(BigDecimal freight, String source, List<BreakdownEntry> breakdown) {
            this.freight = freight;
            this.source = source;
            this.breakdown = breakdown;
        }

        public BigDecimal getFreight() {
            return freight;
        }

        public String getSource() {
            return source;
        }

        public List<BreakdownEntry> getBreakdown() {
            return breakdown;
        }
    }

    /** Cached per-template bundle: header + region rows + free rules (one 5-min cache entry). */
    private record TemplateBundle(LitemallShippingTemplate template,
                                  List<LitemallShippingTemplateRegion> regions,
                                  List<LitemallShippingTemplateFree> freeRules) {
    }

    private final FreightTemplateMapper freightTemplateMapper;
    private final LitemallGoodsMapper goodsMapper;
    private final FreightTemplateProperties properties;

    /** tempId → bundle; templates move rarely, 5 minutes keeps admin edits visible quickly. */
    private final Cache<Integer, Optional<TemplateBundle>> templateCache = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    /** Single-key cache for the default template id (0 sentinel key). */
    private final Cache<Integer, Optional<Integer>> defaultTemplateIdCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    public FreightCalculationService(FreightTemplateMapper freightTemplateMapper,
                                     LitemallGoodsMapper goodsMapper,
                                     FreightTemplateProperties properties) {
        this.freightTemplateMapper = freightTemplateMapper;
        this.goodsMapper = goodsMapper;
        this.properties = properties;
    }

    /** Admin CRUD calls this so template edits are visible without waiting out the TTL. */
    public void invalidateTemplateCache() {
        templateCache.invalidateAll();
        defaultTemplateIdCache.invalidateAll();
    }

    /**
     * Admin dry-run of ONE template (GET /srv/private/admin/freight/preview): price a
     * hypothetical group of {@code quantity} units / {@code amount} value against the
     * template's region + free rules for a destination — no goods binding required.
     * Unknown template → null.
     */
    public BreakdownEntry previewTemplate(int tempId, int quantity, BigDecimal amount,
                                          String countryCode, String provinceName) {
        TemplateBundle bundle = templateCache.get(tempId, k -> loadBundle(tempId)).orElse(null);
        if (bundle == null) {
            return null;
        }
        List<FreightLine> lines = List.of(new FreightLine(0, quantity,
                amount == null || quantity <= 0 ? BigDecimal.ZERO
                        : amount.divide(BigDecimal.valueOf(quantity), 6, RoundingMode.HALF_UP)));
        return priceGroup(bundle, lines, Map.of(), countryCode, provinceName);
    }

    /**
     * Price the freight for one checkout.
     *
     * @param lines        the cart lines (empty/null → flat rule on the subtotal alone)
     * @param countryCode  destination ISO country (null/blank → only '*' region rows match)
     * @param provinceName destination province as free text (matched case-insensitively,
     *                     trimmed, against region rows' {@code province_name})
     * @param subtotal     checked goods subtotal (drives the global free minimum and
     *                     free-rule amount thresholds)
     * @param cjFulfilled  true for CJ carts — templates are skipped entirely by design
     */
    public FreightQuote quote(List<FreightLine> lines, String countryCode, String provinceName,
                              BigDecimal subtotal, boolean cjFulfilled) {
        BigDecimal safeSubtotal = subtotal == null ? BigDecimal.ZERO : subtotal;
        try {
            if (!properties.isEnabled()) {
                return legacyFlat(safeSubtotal, "freight templates disabled — legacy flat rule");
            }
            if (cjFulfilled) {
                return legacyFlat(safeSubtotal, "CJ-fulfilled cart — freight templates do not apply");
            }
            // Global free-shipping minimum stays outermost: the pre-template promise
            // ("free at/above the configured subtotal") must keep holding with templates on.
            if (safeSubtotal.compareTo(SystemConfig.getFreightLimit()) >= 0) {
                return new FreightQuote(BigDecimal.ZERO, SOURCE_FREE_MIN, List.of(new BreakdownEntry(
                        null, null, SOURCE_FREE_MIN, BigDecimal.ZERO,
                        "subtotal ≥ free-shipping minimum (" + SystemConfig.getFreightLimit() + ")")));
            }
            if (lines == null || lines.isEmpty()) {
                return legacyFlat(safeSubtotal, "no line detail supplied — legacy flat rule");
            }
            return templateQuote(lines, countryCode, provinceName, safeSubtotal);
        } catch (RuntimeException e) {
            // The quote endpoint must never 5xx for freight reasons and submit must never
            // die on template data; worst case is the legacy flat rule, clearly labeled.
            log.warn("freight template resolution failed — falling back to flat rule: {}", e.getMessage(), e);
            return legacyFlat(safeSubtotal, "template resolution failed — flat fallback");
        }
    }

    // ---- ladder internals -----------------------------------------------------------

    private FreightQuote legacyFlat(BigDecimal subtotal, String note) {
        if (subtotal.compareTo(SystemConfig.getFreightLimit()) >= 0) {
            return new FreightQuote(BigDecimal.ZERO, SOURCE_FREE_MIN, List.of(new BreakdownEntry(
                    null, null, SOURCE_FREE_MIN, BigDecimal.ZERO, note)));
        }
        BigDecimal flat = SystemConfig.getFreight();
        return new FreightQuote(flat, SOURCE_SYSTEM_FLAT, List.of(new BreakdownEntry(
                null, null, SOURCE_SYSTEM_FLAT, flat, note)));
    }

    private FreightQuote templateQuote(List<FreightLine> lines, String countryCode,
                                       String provinceName, BigDecimal subtotal) {
        // One lean litemall-db batch read for temp_id (+ retail price as the free-rule
        // amount fallback). The goods facade's cross-service DTO carries no freight
        // fields, so the shared DB — which both services already sit on — is the
        // authoritative source for the binding (see the ADR's fetch-sharing note).
        Map<Integer, LitemallGoods> goodsById = batchReadGoods(lines);

        // Group lines: temp_id → group; 0/unbound → default template if set, else flat bucket.
        Integer defaultTempId = defaultTemplateIdCache.get(0, k ->
                Optional.ofNullable(freightTemplateMapper.selectDefaultTemplate())
                        .map(LitemallShippingTemplate::getId)).orElse(null);

        Map<Integer, List<FreightLine>> byTemplate = new LinkedHashMap<>();
        List<FreightLine> flatBucket = new ArrayList<>();
        for (FreightLine line : lines) {
            LitemallGoods goods = goodsById.get(line.goodsId());
            Integer tempId = goods == null ? null : goods.getTempId();
            if (tempId == null || tempId == 0) {
                tempId = defaultTempId;
            }
            if (tempId == null || tempId == 0) {
                flatBucket.add(line);
            } else {
                byTemplate.computeIfAbsent(tempId, k -> new ArrayList<>()).add(line);
            }
        }

        List<BreakdownEntry> breakdown = new ArrayList<>();
        List<BigDecimal> groupAmounts = new ArrayList<>();
        boolean anyTemplate = false;

        for (Map.Entry<Integer, List<FreightLine>> entry : byTemplate.entrySet()) {
            TemplateBundle bundle = templateCache.get(entry.getKey(), k ->
                    loadBundle(entry.getKey())).orElse(null);
            if (bundle == null) {
                // Bound to a template that no longer exists → treat as unbound.
                flatBucket.addAll(entry.getValue());
                continue;
            }
            BreakdownEntry priced = priceGroup(bundle, entry.getValue(), goodsById,
                    countryCode, provinceName);
            breakdown.add(priced);
            groupAmounts.add(priced.getAmount());
            anyTemplate = true;
        }

        if (!flatBucket.isEmpty()) {
            BigDecimal flat = SystemConfig.getFreight();
            breakdown.add(new BreakdownEntry(null, null, SOURCE_SYSTEM_FLAT, flat,
                    "goods without a freight template — legacy flat value"));
            groupAmounts.add(flat);
        }

        if (groupAmounts.isEmpty()) {
            return legacyFlat(subtotal, "no priceable groups — flat fallback");
        }

        BigDecimal total = properties.isSumMode()
                ? groupAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                : groupAmounts.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        String source = anyTemplate ? SOURCE_TEMPLATE : SOURCE_SYSTEM_FLAT;
        return new FreightQuote(scale(total), source, breakdown);
    }

    private BreakdownEntry priceGroup(TemplateBundle bundle, List<FreightLine> groupLines,
                                      Map<Integer, LitemallGoods> goodsById,
                                      String countryCode, String provinceName) {
        LitemallShippingTemplate template = bundle.template();
        // Piece-only v1 (plan §4.4): weight/volume columns are mapped but billing by them
        // is deferred — a type 2/3 template is billed per piece, and says so.
        BigDecimal units = BigDecimal.valueOf(groupLines.stream().mapToInt(FreightLine::quantity).sum());
        BigDecimal groupAmount = groupAmount(groupLines, goodsById);
        String typeNote = template.getType() != null && template.getType() != 1
                ? " (billing type " + template.getType() + " charged per piece — v1)" : "";

        // Free rules first (appoint >= 1): OR semantics — enough units OR enough amount.
        if (template.getAppoint() != null && template.getAppoint() >= 1) {
            for (LitemallShippingTemplateFree free : matchAll(bundle.freeRules(), countryCode, provinceName,
                    LitemallShippingTemplateFree::getCountryCode, LitemallShippingTemplateFree::getProvinceName)) {
                boolean byNumber = positive(free.getNumber()) && units.compareTo(free.getNumber()) >= 0;
                boolean byAmount = positive(free.getPrice()) && groupAmount.compareTo(free.getPrice()) >= 0;
                if (byNumber || byAmount) {
                    return new BreakdownEntry(template.getId(), template.getName(), SOURCE_TEMPLATE,
                            BigDecimal.ZERO, "free rule met ("
                            + (byNumber ? "≥ " + free.getNumber() + " items" : "amount ≥ " + free.getPrice())
                            + ")" + typeNote);
                }
            }
        }

        LitemallShippingTemplateRegion region = bestMatch(bundle.regions(), countryCode, provinceName,
                LitemallShippingTemplateRegion::getCountryCode, LitemallShippingTemplateRegion::getProvinceName);
        if (region == null) {
            // DOCUMENTED DEVIATION: crmeb ships an unmatched region free; we charge the
            // legacy flat value so an incomplete template never gives away shipping.
            BigDecimal flat = SystemConfig.getFreight();
            return new BreakdownEntry(template.getId(), template.getName(), SOURCE_SYSTEM_FLAT, flat,
                    "no region rule matches the destination — flat fallback (crmeb would ship free)" + typeNote);
        }

        BigDecimal amount = firstContinue(units, region);
        return new BreakdownEntry(template.getId(), template.getName(), SOURCE_TEMPLATE, scale(amount),
                "region rule " + describeRegion(region) + ": first " + region.getFirst()
                + " @ " + region.getFirstPrice() + ", per " + region.getContinueP()
                + " more @ " + region.getContinuePrice() + typeNote);
    }

    /** crmeb first/continue: firstPrice + ceil((units - first) / continueP) * continuePrice. */
    private BigDecimal firstContinue(BigDecimal units, LitemallShippingTemplateRegion region) {
        BigDecimal first = orZero(region.getFirst());
        BigDecimal firstPrice = orZero(region.getFirstPrice());
        if (units.compareTo(first) <= 0) {
            return firstPrice;
        }
        BigDecimal continueP = orZero(region.getContinueP());
        if (continueP.compareTo(BigDecimal.ZERO) <= 0) {
            // Defensive: a zero continue-unit would divide by zero; charge one continue step.
            return firstPrice.add(orZero(region.getContinuePrice()));
        }
        BigDecimal steps = units.subtract(first).divide(continueP, 0, RoundingMode.UP);
        return firstPrice.add(steps.multiply(orZero(region.getContinuePrice())));
    }

    /** Group amount for free-rule thresholds: line unit price (cart truth) × qty, retail fallback. */
    private BigDecimal groupAmount(List<FreightLine> groupLines, Map<Integer, LitemallGoods> goodsById) {
        BigDecimal amount = BigDecimal.ZERO;
        for (FreightLine line : groupLines) {
            BigDecimal unit = line.unitPrice();
            if (unit == null) {
                LitemallGoods goods = goodsById.get(line.goodsId());
                unit = goods == null ? BigDecimal.ZERO : orZero(goods.getRetailPrice());
            }
            amount = amount.add(unit.multiply(BigDecimal.valueOf(line.quantity())));
        }
        return amount;
    }

    // ---- region matching --------------------------------------------------------------

    private interface FieldReader<T> {
        String read(T row);
    }

    /**
     * Most specific region row for the destination: exact (country, province) beats
     * (country, any-province) beats the '*' wildcard row. Country compare is
     * case-insensitive; province compare is case-insensitive and trimmed (addresses
     * store free-text international names). Ties break on the lowest row id.
     */
    private <T> T bestMatch(List<T> rows, String countryCode, String provinceName,
                            FieldReader<T> country, FieldReader<T> province) {
        T best = null;
        int bestScore = 0;
        for (T row : rows) {
            int score = matchScore(country.read(row), province.read(row), countryCode, provinceName);
            if (score > bestScore) {
                best = row;
                bestScore = score;
            }
        }
        return best;
    }

    /** All rows matching the destination at any specificity (free rules use OR-any semantics). */
    private <T> List<T> matchAll(List<T> rows, String countryCode, String provinceName,
                                 FieldReader<T> country, FieldReader<T> province) {
        List<T> matched = new ArrayList<>();
        for (T row : rows) {
            if (matchScore(country.read(row), province.read(row), countryCode, provinceName) > 0) {
                matched.add(row);
            }
        }
        return matched;
    }

    /** 3 = (country, province) · 2 = (country, NULL) · 1 = '*' wildcard · 0 = no match. */
    private int matchScore(String rowCountry, String rowProvince, String countryCode, String provinceName) {
        String rc = trimOrNull(rowCountry);
        String rp = trimOrNull(rowProvince);
        String cc = trimOrNull(countryCode);
        String pn = trimOrNull(provinceName);
        if ("*".equals(rc)) {
            return 1; // wildcard row: province qualifier ignored by design (ladder spec)
        }
        if (rc == null || cc == null || !rc.equalsIgnoreCase(cc)) {
            return 0;
        }
        if (rp == null) {
            return 2;
        }
        return pn != null && rp.equalsIgnoreCase(pn) ? 3 : 0;
    }

    // ---- data access ------------------------------------------------------------------

    private Optional<TemplateBundle> loadBundle(Integer tempId) {
        LitemallShippingTemplate template = freightTemplateMapper.selectTemplateById(tempId);
        if (template == null) {
            return Optional.empty();
        }
        return Optional.of(new TemplateBundle(template,
                freightTemplateMapper.selectRegionsByTempId(tempId),
                freightTemplateMapper.selectFreeByTempId(tempId)));
    }

    private Map<Integer, LitemallGoods> batchReadGoods(List<FreightLine> lines) {
        List<Integer> ids = lines.stream().map(FreightLine::goodsId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Integer, LitemallGoods> byId = new HashMap<>();
        if (ids.isEmpty()) {
            return byId;
        }
        LitemallGoodsExample example = new LitemallGoodsExample();
        example.createCriteria().andIdIn(ids);
        for (LitemallGoods goods : goodsMapper.selectByExample(example)) {
            byId.put(goods.getId(), goods);
        }
        return byId;
    }

    // ---- small helpers ------------------------------------------------------------------

    private static String describeRegion(LitemallShippingTemplateRegion region) {
        String country = trimOrNull(region.getCountryCode());
        String province = trimOrNull(region.getProvinceName());
        if ("*".equals(country)) {
            return "(any destination)";
        }
        return "(" + country + (province == null ? "" : ", " + province) + ")";
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
