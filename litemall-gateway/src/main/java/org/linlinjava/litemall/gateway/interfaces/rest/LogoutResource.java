package org.linlinjava.litemall.gateway.interfaces.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/srv/private")
public class LogoutResource {

    private final ReactiveClientRegistrationRepository registrationRepository;

    public LogoutResource(ReactiveClientRegistrationRepository registrations) {
        this.registrationRepository = registrations;
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public Mono<ResponseEntity<Map<String, String>>> logout(
            //@AuthenticationPrincipal(expression = "idToken") OidcIdToken idToken,
            @AuthenticationPrincipal Jwt jwt,
            ServerWebExchange exchange,
            ServerHttpRequest request
            //WebSession session) {
         ){
        //return session.invalidate().then(this.registration.map(oidc -> prepareLogoutUri(request, oidc, idToken)));


        return this.registrationRepository.findByRegistrationId("oidc")
                .flatMap(registration -> {
                    // 1. Invalidate session
                    Mono<Void> sessionInvalidation = exchange.getSession().flatMap(WebSession::invalidate);


                    // 2. Build logout URL
                    String logoutUrl = buildLogoutUrl(registration, jwt, request);


                    Mono<Void> tokenRevocation = revokeTokens(registration, jwt);

                    // 3. Revoke tokens if revocable
                    return Mono.when(sessionInvalidation, tokenRevocation)
                            .thenReturn(ResponseEntity.ok(
                                    Map.of("logout_url", logoutUrl, "message", "Logged out successfully")));
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Client registration not found")));
    }



    private String buildLogoutUrl(ClientRegistration registration, Jwt jwt, ServerHttpRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(registration.getProviderDetails()
                        .getConfigurationMetadata().get("end_session_endpoint").toString())
                .queryParam("id_token_hint", jwt.getTokenValue())
                .queryParam("post_logout_redirect_uri", getSafeRedirectUri(request));

        return builder.toUriString();
    }

    private String getSafeRedirectUri(ServerHttpRequest request) {
        // Use configured redirect URI instead of Origin header
        return "http://localhost:8080/logout-success"; // Configure this
    }

    private Mono<Void> revokeTokens(ClientRegistration registration, Jwt jwt) {
        // Only revoke if token is revocable (has refresh token)
        if (jwt.hasClaim("refresh_token")) {
            return WebClient.create()
                    .post()
                    .uri(registration.getProviderDetails()
                            .getConfigurationMetadata().get("revocation_endpoint").toString())
                    .body(BodyInserters.fromFormData("token", jwt.getTokenValue())
                            .with("client_id", registration.getClientId())
                            .with("client_secret", registration.getClientSecret())
                            .with("token_type_hint", "refresh_token"))
                    .retrieve()
                    .bodyToMono(Void.class);
        }
        return Mono.empty();
    }





   /* private Map<String, String> prepareLogoutUri(ServerHttpRequest request, ClientRegistration clientRegistration, OidcIdToken idToken) {
        StringBuilder logoutUrl = new StringBuilder();

        logoutUrl.append(clientRegistration.getProviderDetails().getConfigurationMetadata().get("end_session_endpoint").toString());

        String originUrl = request.getHeaders().getOrigin();

        logoutUrl.append("?id_token_hint=").append(idToken.getTokenValue()).append("&post_logout_redirect_uri=").append(originUrl);

        return Map.of("logoutUrl", logoutUrl.toString());
    }*/
}
