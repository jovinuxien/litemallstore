package org.linlinjava.litemall.gatewayadmin.security;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.linlinjava.litemall.db.auth.JwtService;
import org.linlinjava.litemall.gatewayadmin.infrastructure.config.security.AuthoritiesConstants;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.auth0.jwt.interfaces.DecodedJWT;

import reactor.core.publisher.Mono;

/**
 * Verifies the admin-edge self-JWT and derives authorities from its
 * {@code roles} claim.
 *
 * <p>Wave 5 split the admin edge into two principal types sharing one realm:
 * {@code typ=admin} (litemall_admin, roles {@code [ROLE_ADMIN]}) and
 * {@code typ=affiliate} (litemall_user promoters, roles
 * {@code [ROLE_AFFILIATE]}). Authorities therefore come FROM the token's
 * {@code roles} claim — never granted unconditionally — so an affiliate token
 * can NEVER act as an admin. Claims outside the known set are dropped; a token
 * whose roles resolve to nothing is rejected. The customer realm uses a
 * distinct issuer/audience and key, so a customer token cannot verify here in
 * the first place; the {@code typ} check is defence-in-depth. No DB hit on the
 * hot path — pure JWT verification.
 */
public class AdminJwtAuthenticationManager implements ReactiveAuthenticationManager {

    /** Token types minted by this edge (AuthController / AffiliateAuthController). */
    private static final Set<String> KNOWN_TYPES = Set.of("admin", "affiliate");

    /** Authorities this edge is willing to reconstruct from a roles claim. */
    private static final Set<String> KNOWN_ROLES =
            Set.of(AuthoritiesConstants.ADMIN, AuthoritiesConstants.AFFILIATE);

    private final JwtService jwt;

    public AdminJwtAuthenticationManager(JwtService adminJwtService) {
        this.jwt = adminJwtService;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = String.valueOf(authentication.getCredentials());
        return Mono.justOrEmpty(jwt.tryVerify(token))
                .filter(d -> KNOWN_TYPES.contains(d.getClaim("typ").asString()))
                .flatMap(d -> Mono.justOrEmpty(toAuthentication(d)))
                .switchIfEmpty(Mono.error(new BadCredentialsException("invalid admin token")));
    }

    /** @return null when the roles claim is absent or carries no known role. */
    private Authentication toAuthentication(DecodedJWT d) {
        List<String> roles = d.getClaim("roles").asList(String.class);
        if (roles == null) {
            return null;
        }
        List<GrantedAuthority> authorities = roles.stream()
                .filter(KNOWN_ROLES::contains)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        if (authorities.isEmpty()) {
            return null;
        }
        return new UsernamePasswordAuthenticationToken(d.getSubject(), null, authorities);
    }
}
