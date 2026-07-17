package org.linlinjava.litemall.authserver.config;

import org.linlinjava.litemall.db.auth.JwtProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Refuses to start the authserver in prod without real JWT keys and client secrets.
 *
 * <p>Wave 7. This is the most important of the three guards, because this service
 * signs EVERY machine token and serves the JWKS that both gateways validate
 * against.
 *
 * <p>The failure it prevents is silent, not loud. {@code application.yml} ships
 * {@code private-key-pem: ""}, so {@link org.linlinjava.litemall.db.auth.RsaKeys#from}
 * falls through to {@code generate()} and mints an EPHEMERAL pair on every boot.
 * {@code JwtService} only logs a WARN. In production that means: every restart
 * silently invalidates all machine tokens, and any other process that cached the
 * JWKS can no longer verify anything this service signs. Nothing errors — traffic
 * just starts failing authentication for no visible reason.
 *
 * <p>Prod-only by design: dev keeps the ephemeral-key convenience.
 */
@Configuration
@Profile("prod")
public class JwtKeyGuard {

    public JwtKeyGuard(JwtProperties jwt, AuthServerProps authServer) {
        requireSet(jwt.getPrivateKeyPem(), "litemall.jwt.private-key-pem",
                "AUTHSERVER_JWT_PRIVATE_KEY_PEM");
        requireSet(jwt.getPublicKeyPem(), "litemall.jwt.public-key-pem",
                "AUTHSERVER_JWT_PUBLIC_KEY_PEM");
        // Plaintext by design (NoOpPasswordEncoder), so a blank secret must never
        // be treated as "no auth required".
        requireSet(authServer.getGatewayApiSecret(), "litemall.authserver.gateway-api-secret",
                "GATEWAY_API_CLIENT_SECRET");
        requireSet(authServer.getGatewayAdminSecret(), "litemall.authserver.gateway-admin-secret",
                "GATEWAY_ADMIN_CLIENT_SECRET");
    }

    private static void requireSet(String value, String property, String envVar) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "FATAL [prod]: " + property + " is not set. Set " + envVar + " (see "
                            + "docker-compose/.env.prod.example). Refusing to start: without it "
                            + "this service would silently sign machine tokens with an ephemeral "
                            + "key pair that dies on every restart.");
        }
    }
}
