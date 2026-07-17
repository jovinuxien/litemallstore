package org.linlinjava.litemall.gatewayapi.config;

import org.linlinjava.litemall.db.auth.JwtProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Refuses to start the customer edge in prod without a real JWT keypair.
 *
 * <p>Wave 7. {@code RsaKeys.from()} silently falls back to generating an
 * EPHEMERAL keypair when the PEMs are blank, and {@code JwtService} only logs a
 * WARN — so a misconfigured prod boots "successfully" and then signs every
 * customer out on each restart, mid-checkout, with no error to explain it.
 *
 * <p>Prod-only: dev keeps the ephemeral-key convenience.
 *
 * <p>New file, deliberately: this worktree (`platform`) does not edit
 * gateway-api's existing config, which the `gateway-api` worktree owns for
 * Wave 7.
 */
@Configuration
@Profile("prod")
public class JwtKeyGuard {

    public JwtKeyGuard(JwtProperties jwt) {
        requireSet(jwt.getPrivateKeyPem(), "litemall.jwt.private-key-pem",
                "GATEWAY_API_JWT_PRIVATE_KEY_PEM");
        requireSet(jwt.getPublicKeyPem(), "litemall.jwt.public-key-pem",
                "GATEWAY_API_JWT_PUBLIC_KEY_PEM");
    }

    private static void requireSet(String value, String property, String envVar) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "FATAL [prod]: " + property + " is not set. Set " + envVar + " (see "
                            + "docker-compose/.env.prod.example). Refusing to start rather than "
                            + "generate an ephemeral customer signing key that logs every "
                            + "customer out on restart.");
        }
    }
}
