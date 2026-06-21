package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto;

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
 * <pre>{ code, result, message, data:{ orderId, orderNum, ... }, requestId }</pre>
 *
 * <p>On a business error CJ keeps the envelope but replaces {@code data} with a NON-object — a String
 * (often the echoed merchant order number) or an empty string — instead of the {@code { ... }} object.
 * A plain bean binding then dies with "Cannot construct instance of Data ... from String value",
 * masking the real {@code message}. {@link LenientDataDeserializer} maps any non-object {@code data}
 * to {@code null} so the caller falls through to the {@code result=false} / {@code message} path.
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
        @JsonProperty("orderNum")
        private String orderNum;
        @JsonProperty("orderStatus")
        private String orderStatus;
    }

    /**
     * Binds {@code data} only when it is a JSON object; any scalar/array (CJ's error-shaped {@code data})
     * yields {@code null}. Reading {@code Data.class} here uses the type's default bean deserializer
     * (this lenient one is registered on the field), so there is no recursion.
     */
    static class LenientDataDeserializer extends JsonDeserializer<Data> {
        @Override
        public Data deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.hasToken(JsonToken.START_OBJECT)) {
                return ctxt.readValue(p, Data.class);
            }
            p.skipChildren(); // consume an array if that ever appears; a no-op for a scalar token
            return null;
        }
    }
}
