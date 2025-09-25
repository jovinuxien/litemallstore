package org.linlinjava.litemall.gateway.infrastructure.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.linlinjava.litemall.gateway.infrastructure.config.security.AuthoritiesConstants;
import org.linlinjava.litemall.gateway.infrastructure.config.security.SecurityUtils;
import org.linlinjava.litemall.gateway.infrastructure.config.security.oauth2.AudienceValidator;
import org.linlinjava.litemall.gateway.infrastructure.config.security.oauth2.JwtGrantedAuthorityConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.web.server.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tech.jhipster.web.filter.reactive.CookieCsrfFilter;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.security.oauth2.core.oidc.StandardClaimNames.PREFERRED_USERNAME;



@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class GatewaySecurityConfig {

    public static final String issuerUri = "http://localhost:9080/realms/jhipster";
    public static final String csp = "default-src 'self'; frame-src 'self' data:; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://storage.googleapis.com; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:";
    private final ReactiveClientRegistrationRepository clientRegistrationRepository;


    private final Cache<String, Mono<Jwt>> users = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(1))
            .recordStats()
            .build();


    public GatewaySecurityConfig(ReactiveClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http, ServerLogoutSuccessHandler handler) {

        http
                .csrf().disable()
                .cors(withDefaults())
                //.csrf(csrf -> csrf
                 //               .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())

                                // See https://stackoverflow.com/q/74447118/65681
                                //.csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler()))
                //)
                .addFilterAt(new CookieCsrfFilter(), SecurityWebFiltersOrder.REACTOR_CONTEXT)
                //.addFilterAfter(new SpaWebFilter(), SecurityWebFiltersOrder.HTTPS_REDIRECT)

                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-src 'self' data:; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://storage.googleapis.com; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:"))
                        .frameOptions(frameOptions -> frameOptions.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
                        .referrerPolicy(
                                referrer ->
                                        referrer.policy(ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                        )
                        .permissionsPolicy(
                                permissions ->
                                        permissions.policy(
                                                "camera=(), fullscreen=(self), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()"
                                        )
                        )
                )

                .authorizeExchange(auth -> auth
                        .pathMatchers("/", "/*.*", "/srv/**", "/srv/authenticate/**", "/srv/catalog/**", "/srv/cjAuth/**").permitAll()
                        .pathMatchers("/srv/private/admin/**").hasAuthority(AuthoritiesConstants.ADMIN)
                        //.pathMatchers("/srv/**").authenticated()
                        .pathMatchers("/srv/private/**").authenticated()
                        //.anyExchange().permitAll())
                )
                //.oauth2Login(Customizer.withDefaults())
                .oauth2Login(oauth2 -> {
                    try {
                        oauth2
                                .authenticationSuccessHandler(authenticationSuccessHandler())
                                .authorizationRequestResolver(authorizationRequestResolver(this.clientRegistrationRepository));
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                })
                .oauth2Client(Customizer.withDefaults())

                .logout((logout) -> logout
                        .logoutSuccessHandler(oidcLogoutSuccessHandler())
                )


                //.oauth2ResourceServer((oauth2) -> oauth2.jwt(Customizer.withDefaults()));
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        // Add this to allow anonymous access to permetted paths
                        .bearerTokenConverter(new ServerBearerTokenAuthenticationConverter())
                )
                //Enable anonymous access
                .anonymous(anonymous -> anonymous
                        .principal(createAnonymousUser())
                        .authorities("ROLE_ANONYMOUS"));
        return http.build();
    }

    // Helper method to create anonymous user principal
    private Principal createAnonymousUser() {
        return new AbstractAuthenticationToken(AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")) {
            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return "anonymousUser";
            }

            @Override
            public boolean isAuthenticated() {
                return false;
            }
        };
    }



    @Bean
    public ServerLogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedServerLogoutSuccessHandler oidcLogoutSuccessHandler =
                new OidcClientInitiatedServerLogoutSuccessHandler(this.clientRegistrationRepository);

        // Sets the location that the End-User's User Agent will be redirected to
        // after the logout has been performed at the Provider
        oidcLogoutSuccessHandler.setPostLogoutRedirectUri("http://localhost:8080/logout");

        return oidcLogoutSuccessHandler;
    }


    @Bean
    public ServerAuthenticationSuccessHandler authenticationSuccessHandler() throws URISyntaxException {
        var handler = new RedirectServerAuthenticationSuccessHandler();
        handler.setLocation(new URI("/login-success"));
        return handler;
    }

    private ServerOAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ReactiveClientRegistrationRepository clientRegistrationRepository
    ) {
        DefaultServerOAuth2AuthorizationRequestResolver authorizationRequestResolver = new DefaultServerOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository
        );
        if (this.issuerUri.contains("auth0.com")) {
            authorizationRequestResolver.setAuthorizationRequestCustomizer(authorizationRequestCustomizer());
        }
        return authorizationRequestResolver;
    }

    private Consumer<OAuth2AuthorizationRequest.Builder> authorizationRequestCustomizer() {
        return customizer ->
                customizer.authorizationRequestUri(
                        uriBuilder -> uriBuilder.queryParam("audience", List.of("account", "api://default")).build()
                );
    }


    Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new JwtGrantedAuthorityConverter());
        jwtAuthenticationConverter.setPrincipalClaimName(PREFERRED_USERNAME);
        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
    }




    @Bean
    public ReactiveOAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        final OidcReactiveOAuth2UserService delegate = new OidcReactiveOAuth2UserService();

        return userRequest -> {
            // Delegate to the default implementation for loading a user
            return delegate
                    .loadUser(userRequest)
                    .map(user -> {
                        Set<GrantedAuthority> mappedAuthorities = new HashSet<>();

                        user
                                .getAuthorities()
                                .forEach(authority -> {
                                    if (authority instanceof OidcUserAuthority) {
                                        OidcUserAuthority oidcUserAuthority = (OidcUserAuthority) authority;
                                        mappedAuthorities.addAll(
                                                SecurityUtils.extractAuthorityFromClaims(oidcUserAuthority.getUserInfo().getClaims())
                                        );
                                        System.out.println("The content of mappedAuthorities are: " + mappedAuthorities);
                                    }
                                });

                        return new DefaultOidcUser(mappedAuthorities, user.getIdToken(), user.getUserInfo(), PREFERRED_USERNAME);
                    });
        };
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(ReactiveClientRegistrationRepository registrations) {
        Mono<ClientRegistration> clientRegistration = registrations.findByRegistrationId("oidc");
        return clientRegistration
                .map(
                        oidc ->
                                createJwtDecoder(
                                        oidc.getProviderDetails().getIssuerUri(),
                                        oidc.getProviderDetails().getJwkSetUri(),
                                        oidc.getProviderDetails().getUserInfoEndpoint().getUri()
                                )
                )
                .block();
    }


    private ReactiveJwtDecoder createJwtDecoder(String issuerUri, String jwkSetUri, String userInfoUri) {

        NimbusReactiveJwtDecoder jwtDecoder = new NimbusReactiveJwtDecoder(jwkSetUri);
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(List.of("account", "api://default"));
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);

        jwtDecoder.setJwtValidator(withAudience);

        return new ReactiveJwtDecoder() {

            @Override
            public Mono<Jwt> decode(String token) throws JwtException {
                return jwtDecoder.decode(token).flatMap(jwt -> enrich(token, jwt));
            }

            private Mono<Jwt> enrich(String token, Jwt jwt) {
                //Look if user information is identity claims are missing
                if (jwt.hasClaim("given_name") && jwt.hasClaim("family_name")) {
                    return Mono.just(jwt);
                }
                // Set user info from `users` cache if present
                return Optional.ofNullable(
                        users.getIfPresent(jwt.getSubject())
                ).orElseGet(() -> {
                    WebClient.create()
                            .get()
                            .uri(userInfoUri)
                            .headers(headers -> headers.setBearerAuth(token))
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                            })
                            .map(userInfo ->
                                    Jwt.withTokenValue(jwt.getTokenValue())
                                            .subject(jwt.getSubject())
                                            .audience(jwt.getAudience())
                                            .headers(headers -> headers.putAll(jwt.getHeaders()))
                                            .claims(claims -> {
                                                String username = userInfo.get("preferred_username").toString();
                                                // Special handling for Auth0
                                                if (userInfo.get("sub").toString().contains("|") && username.contains("@")) {
                                                    userInfo.put("email", username);
                                                }

                                                //Allow full name in a name claim - happens with Auth0
                                                if (userInfo.get("name") != null) {
                                                    String[] name = userInfo.get("name").toString().split("\\s+");
                                                    if (name.length > 0) {
                                                        userInfo.put("given_name", name[0]);
                                                        userInfo.put("family_name", String.join(" ", Arrays.copyOfRange(name, 1, name.length)));
                                                    }
                                                }
                                                claims.putAll(userInfo);
                                            })
                                            .claims(claims -> claims.putAll(jwt.getClaims()))
                                            .build())
                            .doOnNext(newJwt -> users.put(jwt.getSubject(), Mono.just(newJwt)));
                    return Mono.just(jwt);
                });
            }
        };
    }

}
