package org.linlinjava.litemall.order.infrastructure.services.acl.facades.tax;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a tax provider needs to price an order: the destination and the taxable amounts.
 *
 * <p>Deliberately made of primitives rather than cart/order aggregates, like
 * {@code FreightCalculationService}'s {@code FreightLine} — so the preview endpoint and the
 * submit path can both build one from what they already hold, and neither the port nor its
 * adapters need to know what a cart is.
 *
 * <p>Freight is carried separately because it is taxable in many US states and under EU
 * VAT, but at a different rate to goods in some jurisdictions.
 */
public final class TaxableOrder {

    private final List<Line> lines;
    private final BigDecimal freight;
    private final String countryCode;
    private final String provinceName;
    private final String postalCode;
    private final String city;
    private final String addressDetail;
    private final String currency;

    public TaxableOrder(List<Line> lines, BigDecimal freight, String countryCode,
                        String provinceName, String postalCode, String city,
                        String addressDetail, String currency) {
        this.lines = lines;
        this.freight = freight;
        this.countryCode = countryCode;
        this.provinceName = provinceName;
        this.postalCode = postalCode;
        this.city = city;
        this.addressDetail = addressDetail;
        this.currency = currency;
    }

    public List<Line> getLines() {
        return lines;
    }

    public BigDecimal getFreight() {
        return freight;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getProvinceName() {
        return provinceName;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCity() {
        return city;
    }

    public String getAddressDetail() {
        return addressDetail;
    }

    public String getCurrency() {
        return currency;
    }

    /** One taxable line: the catalog-resolved unit price times quantity. */
    public static final class Line {
        private final Integer goodsId;
        private final int quantity;
        private final BigDecimal unitPrice;

        public Line(Integer goodsId, int quantity, BigDecimal unitPrice) {
            this.goodsId = goodsId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public Integer getGoodsId() {
            return goodsId;
        }

        public int getQuantity() {
            return quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public BigDecimal lineTotal() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
}
