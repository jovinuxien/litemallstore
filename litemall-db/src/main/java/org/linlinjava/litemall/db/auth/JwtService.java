package org.linlinjava.litemall.db.auth;

import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Issues and validates self-signed RS256 JWTs for one realm.
 *
 * <p>Built on {@code com.auth0:java-jwt} (already the project's JWT library, see
 * the legacy {@code JwtHelper}). Reused by both edge gateways via a per-realm
 * {@link JwtProperties} bean — the customer edge and admin edge each construct
 * their own instance with distinct issuer/audience so tokens never cross the
 * boundary. Not auto-wired as a bean in litemall-db: each gateway declares
 * its own via {@link #create(JwtProperties)}.
 *
 * <p>This is edge-only end-user auth. It is intentionally separate from the
 * legacy mini-app {@code JwtHelper} (HS256) and from the service-to-service
 * client-credentials machine token (issued by litemall-authserver).
 */
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtProperties props;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final RSAPublicKey publicKey;

    private JwtService(JwtProperties props, RsaKeys keys) {
        this.props = props;
        this.publicKey = keys.getPublicKey();
        this.algorithm = Algorithm.RSA256(keys.getPublicKey(), keys.getPrivateKey());
        this.verifier = JWT.require(algorithm)
                .withIssuer(props.getIssuer())
                .withAudience(props.getAudience())
                .build();
        if (keys.isEphemeral()) {
            log.warn("[jwt:{}] No RSA key pair configured (litemall.jwt.private-key-pem/"
                    + "public-key-pem); generated an EPHEMERAL pair. Tokens are invalidated "
                    + "on restart and unverifiable by other processes. Configure keys for "
                    + "any non-dev use.", props.getIssuer());
        }
    }

    public static JwtService create(JwtProperties props) {
        return new JwtService(props, RsaKeys.from(props.getPrivateKeyPem(), props.getPublicKeyPem()));
    }

    /**
     * Mint an access token for {@code subject} with extra claims (e.g. roles,
     * userId). Sets iss, aud, sub, iat, exp and a random jti.
     */
    public String issue(String subject, Map<String, ?> claims) {
        Instant now = Instant.now();
        JWTCreator.Builder b = JWT.create()
                .withIssuer(props.getIssuer())
                .withAudience(props.getAudience())
                .withSubject(subject)
                .withJWTId(UUID.randomUUID().toString())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(props.getAccessTtlSeconds())));
        if (claims != null) {
            for (Map.Entry<String, ?> e : claims.entrySet()) {
                addClaim(b, e.getKey(), e.getValue());
            }
        }
        return b.sign(algorithm);
    }

    private void addClaim(JWTCreator.Builder b, String name, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String) {
            b.withClaim(name, (String) value);
        } else if (value instanceof Integer) {
            b.withClaim(name, (Integer) value);
        } else if (value instanceof Long) {
            b.withClaim(name, (Long) value);
        } else if (value instanceof Boolean) {
            b.withClaim(name, (Boolean) value);
        } else if (value instanceof String[]) {
            b.withArrayClaim(name, (String[]) value);
        } else {
            b.withClaim(name, String.valueOf(value));
        }
    }

    /** Verify signature, issuer, audience and expiry. Throws on any failure. */
    public DecodedJWT verify(String token) {
        try {
            return verifier.verify(token);
        } catch (JWTVerificationException e) {
            throw new InvalidJwtException("JWT validation failed: " + e.getMessage(), e);
        }
    }

    /** Verify without throwing — empty when invalid. */
    public Optional<DecodedJWT> tryVerify(String token) {
        try {
            return Optional.of(verify(token));
        } catch (InvalidJwtException e) {
            return Optional.empty();
        }
    }

    /** Public key, for later JWKS publication to downstream resource servers. */
    public RSAPublicKey getPublicKey() {
        return publicKey;
    }
}