package org.linlinjava.litemall.db.auth;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads an RSA key pair from PEM, or generates an ephemeral one.
 *
 * <p>PEM parsing strips the {@code -----BEGIN/END-----} armor and decodes the
 * base64 body: PKCS#8 for the private key, X.509 (SubjectPublicKeyInfo) for the
 * public key — the formats produced by
 * {@code openssl genpkey} / {@code openssl rsa -pubout}.
 */
public final class RsaKeys {

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final boolean ephemeral;

    private RsaKeys(RSAPublicKey publicKey, RSAPrivateKey privateKey, boolean ephemeral) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.ephemeral = ephemeral;
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    /** True when keys were generated at startup (not loaded from config). */
    public boolean isEphemeral() {
        return ephemeral;
    }

    /**
     * Build from the configured PEM pair, or generate a 2048-bit ephemeral pair
     * when either side is blank.
     */
    public static RsaKeys from(String privateKeyPem, String publicKeyPem) {
        if (isBlank(privateKeyPem) || isBlank(publicKeyPem)) {
            return generate();
        }
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            RSAPrivateKey priv = (RSAPrivateKey) kf.generatePrivate(
                    new PKCS8EncodedKeySpec(decodePem(privateKeyPem)));
            RSAPublicKey pub = (RSAPublicKey) kf.generatePublic(
                    new X509EncodedKeySpec(decodePem(publicKeyPem)));
            return new RsaKeys(pub, priv, false);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA JWT key pair from PEM", e);
        }
    }

    private static RsaKeys generate() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            return new RsaKeys((RSAPublicKey) kp.getPublic(),
                    (RSAPrivateKey) kp.getPrivate(), true);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate ephemeral RSA JWT key pair", e);
        }
    }

    private static byte[] decodePem(String pem) {
        String body = pem
                .replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
