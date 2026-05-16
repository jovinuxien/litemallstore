package org.linlinjava.litemall.gatewayapi.config;

import org.linlinjava.litemall.db.auth.JwtProperties;
import org.linlinjava.litemall.db.auth.JwtService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the customer-realm {@link JwtService}.
 *
 * <p>Bound from {@code litemall.jwt.*} (issuer/audience are customer-specific,
 * see application.yml) so a customer token is structurally incompatible with an
 * admin token minted by litemall-gateway-admin.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public JwtService customerJwtService(JwtProperties props) {
        return JwtService.create(props);
    }
}