package org.linlinjava.litemall.goods.infrastructure.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * VERIFICATION ONLY. Active under the {@code verify} profile, this permit-all
 * filter chain replaces the litemall-svcsecurity resource-server chain (which
 * backs off via {@code @ConditionalOnMissingBean(SecurityFilterChain.class)}),
 * so the OCS index/search endpoints can be exercised against real MySQL + the
 * docker OCS stack without standing up litemall-authserver. Never active in any
 * real deployment.
 */
@Configuration
@Profile("verify")
public class VerifySecurityConfig {

    @Bean
    public SecurityFilterChain verifySecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
