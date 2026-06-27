package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.io.IOException;

/**
 * CJ Dropshipping {@code createOrder} response envelope:
 * <pre>{ code, result, message, data:{ orderId, orderNumber, ... }, requestId }</pre>
 *
 * <p>CJ is inconsistent about {@code data}:
 * <ul>
 *   <li><b>Success</b> ({@code result=true}) — usually the {@code { orderId, orderNumber, ... }}
 *       object, but CJ may also return {@code data} as a bare orderId <b>string</b>.</li>
 *   <li><b>Business error</b> ({@code result=false}) — CJ replaces {@code data} with a non-object
 *       (a String, often the echoed merchant order number, or an empty string). Plain bean binding
 *       then dies with "Cannot construct instance of Data ... from String value", masking the real
 *       {@code message}.</li>
 * </ul>
 * {@link LenientDataDeserializer} binds an object normally and maps a non-blank scalar to a
 * {@code Data} carrying it as {@code orderId} (so a success-with-string-data still yields the CJ id),
 * leaving the success/failure decision to {@code result} in {@code CjDropshipOrderFacadeImpl} — never
 * to {@code data} being present.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CjCreateOrderResponse {
    private int code;
    private boolean result;
    private String message;
    @JsonDeserialize(using = LenientDataDeserializer.class)
    private Data data;
    private String requestId;

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("orderId")
        private String orderId;
        /** CJ's success object names this {@code orderNumber}; tolerate the older {@code orderNum} too. */
        @JsonProperty("orderNumber")
        @JsonAlias({"orderNum"})
        private String orderNum;
        @JsonProperty("orderStatus")
        private String orderStatus;
    }

    /**
     * Binds {@code data} when it is a JSON object; maps a non-blank scalar to a {@code Data} with that
     * value as {@code orderId} (CJ sometimes returns the order id as a bare string on success); maps a
     * blank string / array to {@code null}. Reading {@code Data.class} here uses the type's default bean
     * deserializer (this lenient one is registered on the field), so there is no recursion.
     */
    static class LenientDataDeserializer extends JsonDeserializer<Data> {
        @Override
        public Data deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.hasToken(JsonToken.START_OBJECT)) {
                return ctxt.readValue(p, Data.class);
            }
            if (p.currentToken() == JsonToken.VALUE_STRING) {
                String value = p.getValueAsString();
                if (value != null && !value.isBlank()) {
                    Data d = new Data();
                    d.setOrderId(value);
                    return d;
                }
                return null;
            }
            p.skipChildren(); // consume an array if that ever appears; a no-op for other scalars
            return null;
        }
    }
}
