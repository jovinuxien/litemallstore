package org.linlinjava.litemall.gatewayadmin.config;

import org.linlinjava.litemall.db.auth.JwtProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Refuses to start the admin edge in prod without a real JWT keypair.
 *
 * <p>Wave 7. {@code RsaKeys.from()} silently falls back to generating an
 * EPHEMERAL keypair when the PEMs are blank, and {@code JwtService} only logs a
 * WARN — so a misconfigured prod boots "successfully" and then logs every admin
 * out on each restart, because the key that signed their token no longer exists.
 *
 * <p>This realm signs ADMIN tokens, and admin RBAC is currently flat (every admin
 * gets ROLE_ADMIN), so a token minted here is fully privileged. Failing fast is
 * the correct behaviour.
 *
 * <p>Prod-only: dev keeps the ephemeral-key convenience.
 */
@Configuration
@Profile("prod")
public class JwtKeyGuard {

    public JwtKeyGuard(JwtProperties jwt) {
        requireSet(jwt.getPrivateKeyPem(), "litemall.jwt.private-key-pem",
                "GATEWAY_ADMIN_JWT_PRIVATE_KEY_PEM");
        requireSet(jwt.getPublicKeyPem(), "litemall.jwt.public-key-pem",
                "GATEWAY_ADMIN_JWT_PUBLIC_KEY_PEM");
    }

    private static void requireSet(String value, String property, String envVar) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "FATAL [prod]: " + property + " is not set. Set " + envVar + " (see "
                            + "docker-compose/.env.prod.example). Refusing to start rather than "
                            + "generate an ephemeral admin signing key that invalidates every "
                            + "admin session on restart.");
        }
    }
}
