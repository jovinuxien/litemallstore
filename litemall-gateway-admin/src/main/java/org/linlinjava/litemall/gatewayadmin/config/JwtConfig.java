package org.linlinjava.litemall.gatewayadmin.config;

import org.linlinjava.litemall.db.auth.JwtProperties;
import org.linlinjava.litemall.db.auth.JwtService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the admin-realm {@link JwtService}.
 *
 * <p>Bound from {@code litemall.jwt.*} (issuer/audience are admin-specific,
 * see application.yml) so an admin token is structurally incompatible with a
 * customer token minted by litemall-gateway-api.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public JwtService adminJwtService(JwtProperties props) {
        return JwtService.create(props);
    }
}
