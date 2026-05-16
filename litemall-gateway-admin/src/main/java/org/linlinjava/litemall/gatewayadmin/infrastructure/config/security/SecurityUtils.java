package org.linlinjava.litemall.gatewayadmin.infrastructure.config.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

public final class SecurityUtils {

    public static final String CLAIMS_NAMESPACE = "https://www.jhipster.tech/";

    private SecurityUtils() {}


    /**
     * Get the login of the current user.
     *
     * @return the login of the current user.
     */
    public static Mono<String> getCurrentUserLogin() {
        return  ReactiveSecurityContextHolder.getContext()
                //.map(context -> context.getAuthentication())
                .map(SecurityContext::getAuthentication)
                .flatMap(authentication -> Mono.justOrEmpty(extractPrincipal(authentication)));

    }

    private static String extractPrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        } else if (authentication.getPrincipal() instanceof UserDetails springSecurityUser) {
            return springSecurityUser.getUsername();
        } else if (authentication instanceof JwtAuthenticationToken) {
            return (String) ((JwtAuthenticationToken) authentication).getToken().getClaims().get("preferred_username");
        } else if (authentication.getPrincipal() instanceof DefaultOidcUser) {
            Map<String, Object> attributes = ((DefaultOidcUser) authentication.getPrincipal()).getAttributes();
            if (attributes.containsKey("preferred_username")) {
                return (String) attributes.get("preferred_username");
            }
        } else if (authentication.getPrincipal() instanceof String s) {
            return s;
        }
        return null;
    }

    public static Mono<String> extractPrincipal2(Authentication authentication){
        return Mono.fromCallable(() -> {
            if(authentication == null){
                return null;
            }

            Object principal = authentication.getPrincipal();

            if(principal instanceof UserDetails userDetails){
                return userDetails.getUsername();
            }
            // Handle JWT case
            if (principal instanceof Jwt jwt) {
                return jwt.getClaimAsString("preferred_username");
            }

            if (principal instanceof OidcUser oidcUser){
                return oidcUser.getPreferredUsername();
            }

            if(principal instanceof String){
                return (String) principal;
            }
            return null;
        });

    }

    /*public static Mono<String> getTokenAuthentication(@RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient authorizedClient) {
        return Mono.just(authorizedClient.getAccessToken().getTokenValue());
    }*/


    public static Mono<String> extractToken(Authentication authentication){
        return Mono.fromCallable(() -> {
            if(authentication == null){
                return null;
            }

            if(authentication instanceof AbstractOAuth2TokenAuthenticationToken<?> tokenAuth){
                return tokenAuth.getToken().getTokenValue();
            }

            if(authentication.getCredentials() instanceof OAuth2AccessToken accessToken){
                return  accessToken.getTokenValue();
            }

            return null;
        });
    }

    public static Mono<String> extractToken2(Authentication authentication) {
        return Mono.fromCallable(() -> {
            if (authentication == null) {
                throw new IllegalArgumentException("Authentication cannot be null");
            }

            if (authentication instanceof AbstractOAuth2TokenAuthenticationToken<?> oauthToken) {
                return oauthToken.getToken().getTokenValue();
            }
            else if (authentication instanceof BearerTokenAuthentication) {
                return ((BearerTokenAuthentication) authentication).getToken().getTokenValue();
            }
            else if (authentication.getCredentials() instanceof String) {
                return (String) authentication.getCredentials();
            }
            throw new IllegalStateException("Unsupported authentication type for token extraction");
        });
    }







    /**
     * Check if a user is authenticated.
     *
     * @return true if the user is authenticated, false otherwise.
     */
    public static Mono<Boolean> isAuthenticated() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getAuthorities)
                .map(authorities -> authorities.stream().map(GrantedAuthority::getAuthority).noneMatch(AuthoritiesConstants.ANONYMOUS::equals));
    }


    /**
     * Checks if the current user has any of the authorities.
     *
     * @param authorities the authorities to check.
     * @return true if the current user has any of the authorities, false otherwise.
     */
    public static Mono<Boolean> hasCurrentUserAnyOfAuthorities(String... authorities) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getAuthorities)
                .map(
                        authorityList ->
                                authorityList
                                        .stream()
                                        .map(GrantedAuthority::getAuthority)
                                        .anyMatch(authority -> Arrays.asList(authorities).contains(authority))
                );
    }


    /**
     * Checks if the current user has none of the authorities.
     *
     * @param authorities the authorities to check.
     * @return true if the current user has none of the authorities, false otherwise.
     */
    public static Mono<Boolean> hasCurrentUserNoneOfZAuthorities(String... authorities) {
        return hasCurrentUserAnyOfAuthorities(authorities).map(result -> !result);
    }

    public static Mono<Boolean> hasCurrentUserThisAuthority(String authority) {
        return hasCurrentUserAnyOfAuthorities(authority);
    }

    public static List<GrantedAuthority> extractAuthorityFromClaims(Map<String, Object> claims) {
        return mapRolesToGrantedAuthorities(getRolesFromClaims(claims));
    }


    private static Collection<String> getRolesFromClaims(Map<String, Object> claims) {
        return (Collection<String>) claims.getOrDefault(
                "groups",
                claims.getOrDefault("roles", claims.getOrDefault(CLAIMS_NAMESPACE + "roles", new ArrayList<>()))
        );
    }

    private static List<GrantedAuthority> mapRolesToGrantedAuthorities(Collection<String> roles) {
        return roles.stream().filter(role -> role.startsWith("ROLE_")).map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }
}
