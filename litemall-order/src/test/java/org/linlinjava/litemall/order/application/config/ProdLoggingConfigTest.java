package org.linlinjava.litemall.order.application.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the one logging line that keeps customer PII out of the prod container log.
 *
 * <p>{@code application.yml} sets {@code org.linlinjava.litemall.db: DEBUG} — every
 * MyBatis statement with its bound parameters (names, addresses, phones, emails,
 * Stripe intent ids). Spring resolves the MOST SPECIFIC logger key, so the prod
 * profile's package-level {@code org.linlinjava.litemall: INFO} does not hold it
 * down; only a {@code .db} key at the same specificity does. This test fails the
 * suite if that key is ever lost in a merge (the goods-management lesson of
 * 2026-08-15, where the same DEBUG firehose wrote 611 MB in two hours).
 */
class ProdLoggingConfigTest {

    @Test
    void prodSilencesTheSqlDebugFirehose() throws Exception {
        String prodYml;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config/application-prod.yml")) {
            assertNotNull(in, "application-prod.yml missing from the classpath");
            prodYml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(prodYml.matches("(?s).*org\\.linlinjava\\.litemall\\.db:\\s*(INFO|WARN|ERROR).*"),
                "application-prod.yml must pin org.linlinjava.litemall.db above DEBUG — "
                        + "application.yml sets it to DEBUG and the more specific key wins");
    }

    @Test
    void devKeepsSqlDebug() throws Exception {
        // The pin is prod-only by design: dev boots print `Preparing:` lines and the
        // runbooks read them. A change here is a deliberate decision, not drift.
        String yml;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config/application.yml")) {
            assertNotNull(in, "application.yml missing from the classpath");
            yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(yml.matches("(?s).*linlinjava:\\s*\\n\\s*litemall:\\s*\\n\\s*db:\\s*DEBUG.*"),
                "application.yml is expected to keep org.linlinjava.litemall.db at DEBUG for dev");
    }
}
