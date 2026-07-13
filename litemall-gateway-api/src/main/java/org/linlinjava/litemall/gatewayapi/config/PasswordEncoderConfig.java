package org.linlinjava.litemall.gatewayapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * ONE shared {@link BCryptPasswordEncoder} for the customer edge — login,
 * register, password change and reset all hash/verify through the same bean
 * (previously an inline {@code new} in CustomerCredentialsService).
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
