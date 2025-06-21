package org.linlinjava.litemall.gateway.infrastructure.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.linlinjava.litemall.gateway.application.security.SecurityUtils;
import org.linlinjava.litemall.gateway.application.security.oauth2.AudienceValidator;
import org.linlinjava.litemall.gateway.application.security.oauth2.JwtGrantedAuthorityConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.TokenRelayGatewayFilterFactory;
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
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
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
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tech.jhipster.config.JHipsterProperties;
import tech.jhipster.web.filter.reactive.CookieCsrfFilter;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

import static org.springframework.security.oauth2.core.oidc.StandardClaimNames.PREFERRED_USERNAME;
import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;
import static org.springframework.web.reactive.function.client.ExchangeStrategies.withDefaults;
//import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
//import org.springframework.security.config.web.server.ServerHttpSecurity;


@Configuration
@EnableWebFluxSecurity
//@EnableReactiveMethodSecurity
// This class is used to configure Spring Security for the gateway application.
public class SecurityConfig {


    /*private final JHipsterProperties jHipsterProperties;

    @Value("${spring.security.oauth2.client.provider.baeldung-keycloak.issuer-uri}")
    private String issuerUri;

    //private final List<String> audiences;


    private final ReactiveClientRegistrationRepository clientRegistrationRepository;
*/

    /*private final Cache<String, Mono<Jwt>> users = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(1))
            .recordStats()
            .build();
*/

   /* public SecurityConfig(ReactiveClientRegistrationRepository clientRegistrationRepository,
                          JHipsterProperties jHipsterProperties) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.jHipsterProperties = jHipsterProperties;
        //audiences = List.of("account", "api://default");
    }*/



   /* @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

        http.securityMatcher(
                new NegatedServerWebExchangeMatcher(
                        new OrServerWebExchangeMatcher(pathMatchers("/app/**", "/i18n/**", "/content/**", "/swagger-ui/**"))
                )
        )
                .cors(Customizer.withDefaults())
                .authorizeExchange(auth ->

                        auth
                                //.pathMatchers("ping").hasAuthority("SCOPE_NICE")
                                .pathMatchers("/goods/**").authenticated()
                                //.anyExchange().authenticated())
                                .anyExchange().permitAll())

                .oauth2Login(Customizer.withDefaults())
                .oauth2ResourceServer((oauth2) -> oauth2.
                        //jwt(Customizer.withDefaults()));
                                jwt(jwt -> jwt.jwtDecoder(jwtDecoder())));
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
        return http.build();
    }*/

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http.authorizeExchange(auth -> auth.anyExchange().authenticated())
                .oauth2Login(Customizer.withDefaults())
                .oauth2ResourceServer((oauth2) -> oauth2.jwt(Customizer.withDefaults()));
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
        return http.build();
    }





    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        return ReactiveJwtDecoders.fromIssuerLocation("http://localhost:9080/realms/baeldung-keycloak");
    }

    @Bean
    public GlobalFilter customGlobalFilter() {
        return (exchange, chain) -> {
            return exchange.getPrincipal()
                    .filter(principal -> principal instanceof Jwt)
                    .cast(Jwt.class)
                    .map(jwt -> jwt.getTokenValue())
                    .map(token -> {
                        exchange.getRequest().mutate()
                                .header("Authorization", "Bearer " + token)
                                .build();
                        return exchange;
                    })
                    .defaultIfEmpty(exchange)
                    .flatMap(chain::filter);
        };
    }


}
