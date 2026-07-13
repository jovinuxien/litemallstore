package org.linlinjava.litemall.order.infrastructure.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Fulfillment-seam configuration (Wave 4, Task D — docs/adr-fulfillment-seams.md):
 * {@code litemall.order.printer.*} and {@code litemall.order.express.*}. Bound with
 * {@code ignoreUnknownFields} (default), so the other {@code litemall.order.*} keys
 * (pickup-enabled, sweeps, ...) bound elsewhere are untouched.
 *
 * <p>Secrets (printer client-secret/machine-secret, OnePass account/secret) are meant to
 * arrive via env vars through relaxed binding — e.g.
 * {@code LITEMALL_ORDER_PRINTER_CLIENT_SECRET}, {@code LITEMALL_ORDER_EXPRESS_ONEPASS_SECRET}
 * — never committed to yml. Provider selection + fail-fast validation happen in
 * {@link FulfillmentSeamsConfiguration}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "litemall.order")
public class FulfillmentProperties {

    private final Printer printer = new Printer();
    private final Express express = new Express();

    @Data
    public static class Printer {
        /** {@code none} (default — logging adapter) or {@code yly}. */
        private String provider = "none";
        /** Auto-print a receipt after every successful payment (AFTER_COMMIT listener). */
        private boolean autoPrint = true;
        /** Shop header line on the receipt. */
        private String businessName = "litemall";
        /** Yly open-platform v2 base URL. */
        private String baseUrl = "https://open-api.10ss.net/v2";
        private String clientId;
        private String clientSecret;
        private String machineCode;
        private String machineSecret;
        private long connectTimeoutMs = 3000;
        private long readTimeoutMs = 6000;
    }

    @Data
    public static class Express {
        /** {@code none} (default), {@code kdniao} (core ExpressService) or {@code onepass}. */
        private String provider = "none";
        /** Caffeine TTL for tracking answers, negatives included. */
        private int cacheMinutes = 30;
        private final Onepass onepass = new Onepass();
        private long connectTimeoutMs = 3000;
        private long readTimeoutMs = 6000;

        @Data
        public static class Onepass {
            private String baseUrl = "https://sms.crmeb.net/api";
            private String account;
            private String secret;
        }
    }
}
