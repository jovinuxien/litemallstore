package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * CJ Dropshipping returns its price fields ({@code sellPrice} / {@code variantSellPrice}) either as
 * a plain number or — for products whose variants span a price range — as a {@code "low-high"}
 * string (e.g. {@code "14.71-64.38"}). A plain {@code Double} field can't bind the range form, so
 * Jackson throws {@code InvalidFormatException} and the whole product-detail fetch fails.
 *
 * <p>This deserializer accepts both: a numeric token binds directly; a {@code "low-high"} string
 * yields the LOW bound (the "from" price); a blank/garbage value yields {@code null}.
 */
public class LenientDoubleDeserializer extends JsonDeserializer<Double> {

    @Override
    public Double deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() != null && p.currentToken().isNumeric()) {
            return p.getDoubleValue();
        }
        String raw = p.getValueAsString();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        // Prices are non-negative, so a '-' after index 0 is a range separator, not a sign.
        int dash = s.indexOf('-', 1);
        if (dash > 0) {
            s = s.substring(0, dash).trim();
        }
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}