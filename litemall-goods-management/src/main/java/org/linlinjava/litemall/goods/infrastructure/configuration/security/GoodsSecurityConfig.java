package org.linlinjava.litemall.goods.infrastructure.configuration.security;


import org.linlinjava.litemall.goods.infrastructure.configuration.security.oauth2.AudienceValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

import static org.springframework.security.oauth2.core.oidc.StandardClaimNames.PREFERRED_USERNAME;

@Configuration
@EnableWebSecurity
//@EnableGlobalMethodSecurity(prePostEnabled =  true, securedEnabled = true)
public class GoodsSecurityConfig {

    public static final String issuerUri = "http://localhost:9080/realms/jhipster";


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(Customizer.withDefaults())
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/", "/*.*").permitAll()
                        .requestMatchers("/srv/**").permitAll()
                        .requestMatchers("/srv/cjAuth/**").permitAll()
                        .requestMatchers("/srv/catalog/**").permitAll()
                        .requestMatchers("/srv/private/admin/**").hasAuthority(AuthoritiesConstants.ADMIN)
                        .requestMatchers("/srv/private/**").authenticated()
                )

                //.antMatchers("/", "/*.*", "/srv/**", "/srv/catalog/**", "/srv/cjAuth/**").permitAll()
                /*.antMatchers("/*.*").permitAll()
                .antMatchers("/srv/**").permitAll()
                .antMatchers("/srv/catalog/**").permitAll()
                .antMatchers("/srv/product/**").permitAll()*/


                //.antMatchers("/srv/private/admin/**").hasAuthority(AuthoritiesConstants.ADMIN)
                //.antMatchers("/srv/private/**").authenticated()
                //.anyRequest().permitAll()

                //.and()

                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter())));
                //.oauth2Client(Customizer.withDefaults());

        return http.build();
    }




    Converter<Jwt, AbstractAuthenticationToken> authenticationConverter() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new JwtGrantedAuthorityConverter());
        jwtAuthenticationConverter.setPrincipalClaimName(PREFERRED_USERNAME);
        return jwtAuthenticationConverter;
    }


    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromOidcIssuerLocation(issuerUri);

        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(List.of("account", "api://default"));
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);

        jwtDecoder.setJwtValidator(withAudience);

        return jwtDecoder;
    }

}
