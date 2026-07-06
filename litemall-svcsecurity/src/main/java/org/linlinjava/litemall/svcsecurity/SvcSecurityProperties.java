package org.linlinjava.litemall.svcsecurity;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resource-server settings shared by every DDD service.
 *
 * <p>Bound from {@code litemall.svcsecurity.*}. {@code jwkSetUri} points at the
 * litemall-authserver JWKS endpoint (machine tokens). Paths default to the
 * litemall convention used by the former Keycloak {@code GoodsSecurityConfig}.
 */
@ConfigurationProperties(prefix = "litemall.svcsecurity")
public class SvcSecurityProperties {

    /** JWKS endpoint of litemall-authserver (e.g. http://localhost:8089/oauth2/jwks). */
    private String jwkSetUri;

    /** Publicly reachable paths (no machine token required). */
    private List<String> publicPaths = List.of(
            "/actuator/health/**",
            "/srv/authenticate/**",
            "/srv/catalog/**",
            "/srv/goods/**",
            "/srv/search/**",
            "/srv/suggest/**",
            "/srv/cjAuth/**");

    /** Paths requiring the forwarded end-user to be an admin (ROLE_ADMIN). */
    private List<String> adminPaths = List.of("/srv/private/admin/**");

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public List<String> getAdminPaths() {
        return adminPaths;
    }

    public void setAdminPaths(List<String> adminPaths) {
        this.adminPaths = adminPaths;
    }
}
