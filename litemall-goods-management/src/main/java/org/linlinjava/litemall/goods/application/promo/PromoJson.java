package org.linlinjava.litemall.goods.application.promo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

/**
 * Tiny JSON helpers for the promo-candidate {@code suggestion}/{@code reasons} columns.
 * Serialization failures degrade to null/empty — a proposal row with a lost suggestion
 * is still listable and dismissible, never a thrown 5xx.
 */
final class PromoJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PromoJson() {
    }

    static String object(Map<String, Object> value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    static String array(List<String> values) {
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception e) {
            return null;
        }
    }

    static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    static List<String> parseArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
