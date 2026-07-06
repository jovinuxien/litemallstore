package org.linlinjava.litemall.authserver.config;

import java.time.Duration;
import java.util.UUID;

import org.linlinjava.litemall.db.auth.JwtProperties;
import org.linlinjava.litemall.db.auth.RsaKeys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * Authorization server wiring: {@code client_credentials} only, RS256.
 *
 * <p>The signing key reuses the shared {@link RsaKeys}/{@link JwtProperties}
 * PEM convention (same as the edge gateways): supply
 * {@code litemall.jwt.private-key-pem}/{@code public-key-pem} for a stable key,
 * otherwise an ephemeral pair is generated (dev only — restart invalidates
 * issued machine tokens and forces resource servers to re-fetch JWKS).
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, AuthServerProps.class})
public class AuthorizationServerConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/oauth2/jwks").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    /** Dev: plaintext client secrets. Override secrets via config for non-dev. */
    @Bean
    @SuppressWarnings("deprecation")
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(AuthServerProps props) {
        return new InMemoryRegisteredClientRepository(
                machineClient("gateway-admin", props.getGatewayAdminSecret()),
                machineClient("gateway-api", props.getGatewayApiSecret()));
    }

    private RegisteredClient machineClient(String clientId, String secret) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(secret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("svc")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofMinutes(10))
                        .build())
                .build();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(JwtProperties jwt) {
        RsaKeys keys = RsaKeys.from(jwt.getPrivateKeyPem(), jwt.getPublicKeyPem());
        RSAKey rsa = new RSAKey.Builder(keys.getPublicKey())
                .privateKey(keys.getPrivateKey())
                .keyID(UUID.randomUUID().toString())
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsa));
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        // Spring Authorization Server requires the issuer to be a valid absolute
        // URL (it backs the /.well-known, /oauth2/token, /oauth2/jwks metadata).
        // litemall.jwt.issuer ("litemall-authserver") is only the JWT `iss` *claim*
        // name and is NOT a URL, so we must not pass it here. Omitting issuer() lets
        // the server derive it per-request; resource servers (litemall-svcsecurity)
        // validate via jwkSetUri only and do not check the `iss` claim.
        return AuthorizationServerSettings.builder()
                .build();
    }
}
