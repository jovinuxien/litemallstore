package org.linlinjava.litemall.db.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for a single self-signed JWT realm.
 *
 * <p>Each edge gateway (customer / admin) owns one of these. The customer edge
 * (litemall-gateway-api) and the admin edge (litemall-gateway-admin) configure
 * their own issuer/audience so a token minted for one realm is rejected by the
 * other. Keys are RS256 (asymmetric) so the public key can later be published
 * as a JWKS for downstream resource servers without sharing a signing secret.
 *
 * <p>Bound from {@code litemall.jwt.*}. If no key pair is supplied an ephemeral
 * one is generated at startup (dev convenience only — tokens do not survive a
 * restart and cannot be validated by another process).
 *
 * <p>Lives in litemall-db rather than litemall-core: litemall-core pulls
 * spring-boot-starter-web (servlet MVC), which is incompatible with the
 * reactive Spring Cloud Gateway; litemall-db is the lowest servlet-free module
 * shared by both gateways and the DDD services.
 */
@ConfigurationProperties(prefix = "litemall.jwt")
public class JwtProperties {

    /** Token issuer ("iss"). Distinct per realm, e.g. litemall-customer / litemall-admin. */
    private String issuer = "litemall";

    /** Expected audience ("aud"). Distinct per realm. */
    private String audience = "litemall";

    /** Access-token lifetime in seconds. Default 2h, matching legacy JwtHelper. */
    private long accessTtlSeconds = 7200L;

    /** PKCS#8 PEM RSA private key (signing). Empty -> ephemeral key pair. */
    private String privateKeyPem = "";

    /** X.509 PEM RSA public key (verification). Empty -> ephemeral key pair. */
    private String publicKeyPem = "";

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public void setAccessTtlSeconds(long accessTtlSeconds) {
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public void setPrivateKeyPem(String privateKeyPem) {
        this.privateKeyPem = privateKeyPem;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }
}
