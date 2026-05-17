package org.linlinjava.litemall.svcsecurity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Promotes the gateway-forwarded end-user identity to the security context —
 * but ONLY when the request is already authenticated with a valid machine
 * token (a {@link JwtAuthenticationToken} from the authserver JWKS resource
 * server). Without a valid machine token the {@code X-User-*} headers are
 * never trusted: they are simply ignored, so a spoofed header cannot grant
 * access.
 *
 * <p>Runs right after the bearer-token filter. When a machine token is present
 * and {@code X-User-Id} is set, the context authentication is replaced with
 * the forwarded user (principal = user id, authorities = {@code X-User-Roles}).
 * A machine-only call (no {@code X-User-*}) keeps its {@code JwtAuthenticationToken}.
 */
public class MachineTokenUserContextFilter extends OncePerRequestFilter {

    public static final String HDR_USER_ID = "X-User-Id";
    public static final String HDR_USER_TYPE = "X-User-Type";
    public static final String HDR_USER_ROLES = "X-User-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        boolean machineTokenValid = current instanceof JwtAuthenticationToken && current.isAuthenticated();

        if (machineTokenValid) {
            String userId = request.getHeader(HDR_USER_ID);
            if (userId != null && !userId.isBlank()) {
                List<GrantedAuthority> authorities = parseRoles(request.getHeader(HDR_USER_ROLES));
                UsernamePasswordAuthenticationToken userAuth =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                userAuth.setDetails(request.getHeader(HDR_USER_TYPE));
                SecurityContextHolder.getContext().setAuthentication(userAuth);
            }
        }
        chain.doFilter(request, response);
    }

    private List<GrantedAuthority> parseRoles(String rolesHeader) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (rolesHeader != null && !rolesHeader.isBlank()) {
            for (String role : rolesHeader.split(",")) {
                String trimmed = role.trim();
                if (!trimmed.isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority(trimmed));
                }
            }
        }
        return authorities;
    }
}
