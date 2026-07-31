package org.linlinjava.litemall.gatewayapi.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.linlinjava.litemall.gatewayapi.auth.AccountService.AccountException;
import org.linlinjava.litemall.gatewayapi.auth.AccountService.GoogleIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Verifies a Google Sign-In ID token and returns the verified identity
 * (Wave 16). Env-gated exactly like the rest of the optional integrations:
 * {@code litemall.google.client-id} blank ⇒ 707 on any attempt — the SPA never
 * shows the button in that case (site-config serves null), so 707 only meets
 * hand-rolled requests.
 *
 * <p>Verification is delegated to Google's own {@code tokeninfo} endpoint
 * (signature + expiry checked by Google); we then assert what tokeninfo cannot
 * know for us: the audience is OUR client id, the issuer is Google, and the
 * email is verified. Google documents tokeninfo as appropriate below ~100
 * logins/minute; if that ever binds, swap the transport for local JWKS
 * validation behind this same seam — callers only see {@link #verify}.
 *
 * <p>Failure is always typed ({@link AccountException} 707/708) — never a 5xx,
 * never a fake success.
 */
@Component
public class GoogleTokenVerifier {

    private static final Set<String> GOOGLE_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private static final URI TOKENINFO = URI.create("https://oauth2.googleapis.com/tokeninfo");

    private final String clientId;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public GoogleTokenVerifier(@Value("${litemall.google.client-id:}") String clientId) {
        this.clientId = clientId == null || clientId.isBlank() ? null : clientId.trim();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public boolean isEnabled() {
        return clientId != null;
    }

    /** Blocking — call on boundedElastic like the rest of the auth flows. */
    public GoogleIdentity verify(String credential) {
        if (clientId == null) {
            throw new AccountException(AccountService.ERR_GOOGLE_DISABLED, "Google Sign-In is not available");
        }
        if (credential == null || credential.isBlank() || credential.length() > 4096) {
            throw new AccountException(AccountService.ERR_GOOGLE_INVALID, "invalid Google credential");
        }
        Map<String, Object> claims = fetchClaims(credential.trim());
        if (!GOOGLE_ISSUERS.contains(str(claims.get("iss"))) || !clientId.equals(str(claims.get("aud")))) {
            throw new AccountException(AccountService.ERR_GOOGLE_INVALID, "Google credential was not issued for this site");
        }
        String sub = str(claims.get("sub"));
        String email = str(claims.get("email"));
        boolean emailVerified = "true".equalsIgnoreCase(str(claims.get("email_verified")));
        if (sub == null || sub.isBlank() || email == null || !emailVerified) {
            throw new AccountException(AccountService.ERR_GOOGLE_INVALID, "Google account email is not verified");
        }
        return new GoogleIdentity(sub, email, str(claims.get("name")), str(claims.get("picture")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchClaims(String credential) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TOKENINFO + "?id_token=" + URLEncoder.encode(credential, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // Google answers 4xx for malformed/expired tokens.
                throw new AccountException(AccountService.ERR_GOOGLE_INVALID, "Google rejected the credential");
            }
            return mapper.readValue(response.body(), Map.class);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AccountException(AccountService.ERR_GOOGLE_INVALID, "could not verify the Google credential — please try again");
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
