package org.linlinjava.litemall.order.infrastructure.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Fulfillment- and money-seam configuration (Wave 4, Task D —
 * docs/adr-fulfillment-seams.md; Wave 7 adds stripe/tax —
 * docs/adr-stripe-payments.md): {@code litemall.order.printer.*},
 * {@code litemall.order.express.*}, {@code litemall.order.stripe.*} and
 * {@code litemall.order.tax.*}. Bound with {@code ignoreUnknownFields} (default), so the
 * other {@code litemall.order.*} keys (pickup-enabled, sweeps, ...) bound elsewhere are
 * untouched.
 *
 * <p>Secrets (printer client-secret/machine-secret, OnePass account/secret, the Stripe
 * secret + webhook secret) are meant to arrive via env vars through relaxed binding — e.g.
 * {@code LITEMALL_ORDER_PRINTER_CLIENT_SECRET}, {@code LITEMALL_ORDER_STRIPE_SECRET_KEY}
 * — never committed to yml. Provider selection + fail-fast validation happen in
 * {@link FulfillmentSeamsConfiguration}.
 *
 * <p>Note this is deliberately NOT litemall-core's {@code litemall.stripe.*}
 * ({@code application-core.yml}), which ships committed test keys, binds no webhook field
 * and is read by nothing but a global {@code Stripe.apiKey} assignment. order is the only
 * consumer of Stripe, so it owns its own namespace; the core block's removal/rotation is
 * platform's.
 */
@Data
@Component
@ConfigurationProperties(prefix = "litemall.order")
public class FulfillmentProperties {

    private final Printer printer = new Printer();
    private final Express express = new Express();
    private final Stripe stripe = new Stripe();
    private final Tax tax = new Tax();

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

    /**
     * Stripe card payments. Disabled by default: card pay then returns a typed error and
     * NEVER a fake success. Wallet checkout is unaffected either way.
     */
    @Data
    public static class Stripe {
        /** Master switch. false ⇒ the disabled adapter: no Stripe call is ever made. */
        private boolean enabled = false;
        /** sk_live_/sk_test_. ENV ONLY — no committed fallback. */
        private String secretKey;
        /** whsec_... — the webhook signing secret. ENV ONLY; without it the webhook rejects everything. */
        private String webhookSecret;
        /** ISO currency asserted against the PaymentIntent. A mismatch is a rejected payment. */
        private String currency = "usd";
    }

    /**
     * Tax (US sales tax / EU VAT).
     *
     * <p><b>Fails closed</b>, unlike every other seam here: when enabled and the provider
     * is unreachable, checkout is BLOCKED rather than shipping an untaxed order. A missed
     * receipt is recoverable; an untaxed sale is a liability.
     */
    @Data
    public static class Tax {
        /** {@code none} (default — the zero-tax adapter) or {@code stripe}. */
        private String provider = "none";
        /** Master switch. false ⇒ taxPrice is 0.00 and checkout proceeds untaxed (dev default). */
        private boolean enabled = false;
        /** Merchant origin address, required by Stripe Tax to source a US sale. */
        private String originCountry = "US";
        private String originPostalCode;
        private String originState;
    }
}
