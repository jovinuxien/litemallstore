package org.linlinjava.litemall.svcsecurity;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Shared resource-server security for every DDD service.
 *
 * <p>Validates the machine JWT against the authserver JWKS
 * ({@code litemall.svcsecurity.jwk-set-uri}), then layers
 * {@link MachineTokenUserContextFilter} so the gateway-forwarded
 * {@code X-User-*} identity is honoured ONLY behind a valid machine token.
 * Public paths need neither; admin paths require the forwarded
 * {@code ROLE_ADMIN}. Backs off entirely if a service declares its own
 * {@link SecurityFilterChain}.
 */
@AutoConfiguration
@EnableWebSecurity
@EnableConfigurationProperties(SvcSecurityProperties.class)
public class SvcSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain svcSecurityFilterChain(HttpSecurity http,
                                                      SvcSecurityProperties props) throws Exception {
        String[] publicPaths = props.getPublicPaths().toArray(new String[0]);
        String[] adminPaths = props.getAdminPaths().toArray(new String[0]);

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPaths).permitAll()
                        .requestMatchers(adminPaths).hasAuthority("ROLE_ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwkSetUri(props.getJwkSetUri())))
                .addFilterAfter(new MachineTokenUserContextFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
