package org.linlinjava.litemall.gatewayadmin.security;

import java.util.List;

import org.linlinjava.litemall.db.auth.JwtService;
import org.linlinjava.litemall.gatewayadmin.infrastructure.config.security.AuthoritiesConstants;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.auth0.jwt.interfaces.DecodedJWT;

import reactor.core.publisher.Mono;

/**
 * Verifies the admin self-JWT and grants {@code ROLE_ADMIN}.
 *
 * <p>Minimal claim model: a token that verifies against the admin realm
 * ({@code adminJwtService}) and carries {@code typ=admin} is an admin. The
 * customer realm uses a distinct issuer/audience and key, so a customer token
 * cannot verify here in the first place; the {@code typ} check is
 * defence-in-depth. No DB hit on the hot path — pure JWT verification.
 */
public class AdminJwtAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtService jwt;

    public AdminJwtAuthenticationManager(JwtService adminJwtService) {
        this.jwt = adminJwtService;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = String.valueOf(authentication.getCredentials());
        return Mono.justOrEmpty(jwt.tryVerify(token))
                .filter(d -> "admin".equals(d.getClaim("typ").asString()))
                .map(this::toAuthentication)
                .switchIfEmpty(Mono.error(new BadCredentialsException("invalid admin token")));
    }

    private Authentication toAuthentication(DecodedJWT d) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                d.getSubject(), null,
                List.of(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN)));
        return auth;
    }
}
