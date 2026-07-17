package org.linlinjava.litemall.order.infrastructure.acl.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.tax.Calculation;
import com.stripe.net.RequestOptions;
import com.stripe.param.tax.CalculationCreateParams;
import org.linlinjava.litemall.order.application.util.exception.tax.LitemallTaxUnavailableException;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.TaxCalculationPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.tax.TaxQuote;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.tax.TaxableOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Stripe Tax implementation of the tax seam (Wave 7, Task C): US sales tax and EU VAT from
 * one provider, sourced from the destination address.
 *
 * <p>Constructed by {@code FulfillmentSeamsConfiguration}, not component-scanned, so exactly
 * one {@link TaxCalculationPort} bean exists (module convention). Uses per-call
 * {@link RequestOptions} rather than the global {@code Stripe.apiKey}, for the same reason
 * as {@link StripePaymentGatewayAdapter}.
 *
 * <p>Every failure path throws {@link LitemallTaxUnavailableException}: this adapter has no
 * fallback by design. Returning zero on error would be indistinguishable from "no tax due"
 * and would ship untaxed orders during an outage.
 */
public class StripeTaxAdapter implements TaxCalculationPort {

    private static final Logger log = LoggerFactory.getLogger(StripeTaxAdapter.class);

    private final RequestOptions requestOptions;
    private final String currency;

    public StripeTaxAdapter(String secretKey, String currency) {
        this.requestOptions = RequestOptions.builder().setApiKey(secretKey).build();
        this.currency = currency.toLowerCase();
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public TaxQuote quote(TaxableOrder order) {
        if (order.getCountryCode() == null || order.getCountryCode().isBlank()) {
            // Stripe cannot source a sale without a destination country, and guessing one
            // would mean guessing a tax rate.
            throw new LitemallTaxUnavailableException(
                    "Tax cannot be calculated without a destination country — choose a shipping address.");
        }

        try {
            CalculationCreateParams.Builder params = CalculationCreateParams.builder()
                    .setCurrency(currency)
                    .setCustomerDetails(CalculationCreateParams.CustomerDetails.builder()
                            .setAddress(buildAddress(order))
                            .setAddressSource(CalculationCreateParams.CustomerDetails.AddressSource.SHIPPING)
                            .build());

            for (TaxableOrder.Line line : order.getLines()) {
                params.addLineItem(CalculationCreateParams.LineItem.builder()
                        .setAmount(toMinorUnits(line.lineTotal()))
                        .setQuantity((long) line.getQuantity())
                        .setReference(String.valueOf(line.getGoodsId()))
                        .build());
            }

            // Shipping is taxable in many US states and under EU VAT, often at its own
            // rate — hand it to Stripe rather than folding it into a line.
            if (order.getFreight() != null && order.getFreight().signum() > 0) {
                params.setShippingCost(CalculationCreateParams.ShippingCost.builder()
                        .setAmount(toMinorUnits(order.getFreight()))
                        .build());
            }

            Calculation calculation = Calculation.create(params.build(), requestOptions);
            BigDecimal tax = fromMinorUnits(calculation.getTaxAmountExclusive());
            log.info("Stripe Tax: {} {} on a {} order to {}",
                    tax, currency, order.getLines().size(), order.getCountryCode());
            return new TaxQuote(tax, serializeBreakdown(calculation), calculation.getId());

        } catch (StripeException e) {
            log.error("Stripe Tax calculation FAILED for a {} order — blocking checkout: {}",
                    order.getCountryCode(), e.getMessage());
            throw new LitemallTaxUnavailableException(
                    "Tax could not be calculated right now, so the order was not placed. "
                    + "Please try again in a moment.", e);
        }
    }

    private CalculationCreateParams.CustomerDetails.Address buildAddress(TaxableOrder order) {
        CalculationCreateParams.CustomerDetails.Address.Builder address =
                CalculationCreateParams.CustomerDetails.Address.builder()
                        .setCountry(order.getCountryCode());
        if (notBlank(order.getPostalCode())) {
            address.setPostalCode(order.getPostalCode());
        }
        if (notBlank(order.getProvinceName())) {
            address.setState(order.getProvinceName());
        }
        if (notBlank(order.getCity())) {
            address.setCity(order.getCity());
        }
        if (notBlank(order.getAddressDetail())) {
            address.setLine1(order.getAddressDetail());
        }
        return address.build();
    }

    /**
     * The provider's per-jurisdiction lines, kept for invoices and audit. Hand-built rather
     * than {@code toJson()}'d: the full Calculation is large and carries customer details we
     * have no reason to copy into our own table.
     */
    private String serializeBreakdown(Calculation calculation) {
        if (calculation.getTaxBreakdown() == null || calculation.getTaxBreakdown().isEmpty()) {
            return null;
        }
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Calculation.TaxBreakdown row : calculation.getTaxBreakdown()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"amount\":").append(row.getAmount())
                    .append(",\"taxable_amount\":").append(row.getTaxableAmount())
                    .append(",\"inclusive\":").append(row.getInclusive())
                    .append('}');
        }
        return json.append(']').toString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static long toMinorUnits(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private static BigDecimal fromMinorUnits(Long minor) {
        return BigDecimal.valueOf(minor == null ? 0L : minor).movePointLeft(2).setScale(2);
    }
}
