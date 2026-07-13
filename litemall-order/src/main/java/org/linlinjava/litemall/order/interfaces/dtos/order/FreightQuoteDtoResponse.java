package org.linlinjava.litemall.order.interfaces.dtos.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.linlinjava.litemall.order.application.internal.FreightCalculationService;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Checkout freight/logistics quote. {@code freightPrice} is what submit will actually charge
 * — both surfaces price through {@link FreightCalculationService} (template ladder when
 * enabled, else the legacy {@code litemall_express_freight_min/value} rule). {@code source}
 * + {@code breakdown} (Wave 4) explain the figure: {@code TEMPLATE} groups, the
 * {@code SYSTEM_FLAT} fallback, or the global {@code FREE_MIN} rule. {@code cj} is
 * informational only — carrier + delivery estimate; CJ's own shipping cost is internal
 * and deliberately absent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class FreightQuoteDtoResponse {

    /** The freight the order will be charged at submit for this cart group. */
    private final BigDecimal freightPrice;

    /** Goods subtotal at/above which shipping is free (the configured threshold). */
    private final BigDecimal freeShippingThreshold;

    /** Overall pricing source: TEMPLATE | SYSTEM_FLAT | FREE_MIN (Wave 4). */
    private final String source;

    /** Per-group pricing detail (template groups + flat bucket); never null (Wave 4). */
    private final List<BreakdownItem> breakdown;

    /** The CJ logistics estimate for a CJ cart group; null when unavailable / not requested. */
    private final CjInfo cj;

    /** Set when a CJ quote was requested but could not be produced (outage, no lane, bad line). */
    private final String cjNote;

    public FreightQuoteDtoResponse(BigDecimal freightPrice, BigDecimal freeShippingThreshold,
                                   String source, List<BreakdownItem> breakdown,
                                   CjInfo cj, String cjNote) {
        this.freightPrice = freightPrice;
        this.freeShippingThreshold = freeShippingThreshold;
        this.source = source;
        this.breakdown = breakdown;
        this.cj = cj;
        this.cjNote = cjNote;
    }

    /** Build the quote DTO from the calculator's decision (+ the CJ informational block). */
    public static FreightQuoteDtoResponse fromQuote(FreightCalculationService.FreightQuote quote,
                                                    BigDecimal freeShippingThreshold,
                                                    CjInfo cj, String cjNote) {
        return new FreightQuoteDtoResponse(
                quote.getFreight(),
                freeShippingThreshold,
                quote.getSource(),
                quote.getBreakdown().stream().map(BreakdownItem::from).collect(Collectors.toList()),
                cj, cjNote);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Getter
    @AllArgsConstructor
    public static class BreakdownItem {
        private final Integer templateId;
        private final String templateName;
        /** TEMPLATE | SYSTEM_FLAT | FREE_MIN for this group. */
        private final String source;
        private final BigDecimal amount;
        private final String note;

        static BreakdownItem from(FreightCalculationService.BreakdownEntry entry) {
            return new BreakdownItem(entry.getTemplateId(), entry.getTemplateName(),
                    entry.getSource(), entry.getAmount(), entry.getNote());
        }
    }

    @Getter
    @AllArgsConstructor
    public static class CjInfo {
        private final String logisticName;
        /** Delivery-time estimate as CJ reports it, e.g. "8-12" (days). */
        private final String logisticAging;
    }
}
