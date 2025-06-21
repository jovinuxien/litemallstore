package org.linlinjava.litemall.goods.infrastructure.configuration.security;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
@EnableWebSecurity
//@EnableMethodSecurity(prePostEnabled = true)
@EnableGlobalMethodSecurity(prePostEnabled =  true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {



    /*@Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                                //.antMatchers("/goods/ping").hasAuthority("ROLE_NICE")
                                .anyRequest().authenticated())
                .oauth2ResourceServer((oauth2) ->
                        //oauth2.jwt(Customizer.withDefaults()));
                        oauth2.jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }*/

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .antMatchers("/goods/ping").hasAnyAuthority("ROLE_NICE", "account.view-profile")
                .anyRequest().authenticated() // OR .access("authenticated AND hasRole('product_read')")
                .and()
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 ->
                        oauth2
                                .jwt(jwt -> jwt
                        .decoder(jwtDecoder())
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())));
    }



    @Bean
    public JwtDecoder jwtDecoder() {
        return JwtDecoders.fromIssuerLocation("http://localhost:9080/realms/baeldung-keycloak");
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
      /*  JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");*/

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        //converter.setJwtGrantedAuthoritiesConverter(new RealmRoleConverter());
        converter.setJwtGrantedAuthoritiesConverter(new RealmRoleResourceConverter());
        //converter.setPrincipalClaimName("preferred_username");
        return converter;
    }



   /* @Bean
    SecurityFilterChain resourceServerSecurityFilterChain(
            HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken> authenticationConverter) throws Exception {
        http.oauth2ResourceServer(resourceServer -> {
            resourceServer.jwt(jwtDecoder -> {
                jwtDecoder.jwtAuthenticationConverter(authenticationConverter);
            });
        });

        http.sessionManagement(sessions -> {
            sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        }).csrf(csrf -> {
            csrf.disable();
        });

        http.authorizeHttpRequests(requests -> {
            requests.antMatchers("goods/ping").authenticated();
            requests.anyRequest().denyAll();
        });

        return http.build();
    }*/

}
