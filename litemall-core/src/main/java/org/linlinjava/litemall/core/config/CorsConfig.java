package org.linlinjava.litemall.core.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class CorsConfig  {
    // 当前跨域请求最大有效时长。这里默认30天
    private long maxAge = 30 * 24 * 60 * 60;


    /*private CorsConfiguration buildConfig() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.addAllowedOrigin("*"); // 1 Set access source address
        corsConfiguration.addAllowedHeader("*"); // 2 Set the access origin request header
        corsConfiguration.addAllowedMethod("*"); // 3 Set the access source request method
        corsConfiguration.setMaxAge(maxAge);
        corsConfiguration.setAllowCredentials(true);
        return corsConfiguration;
    }*/

    //@Bean
   /* public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", buildConfig()); // 4 Configure cross-domain settings for interfaces
        return new CorsFilter(source);
    }*/
}
