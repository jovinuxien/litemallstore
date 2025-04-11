package org.linlinjava.litemall.admin.config;

import org.springframework.context.annotation.Configuration;

@Configuration
//@EnableWebSecurity
//public class SecurityConfig extends WebSecurityConfigurerAdapter {
public class SecurityConfig  {

    /* private final AdminUserDetailsService adminUserDetailsService */;


    /* public SecurityConfig(AdminUserDetailsService adminUserDetailsService) {
        this.adminUserDetailsService = adminUserDetailsService;
    } */

    /* public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .antMatchers("/admin/auth/kaptcha", "/admin/auth/login", "/admin/auth/401",
                        "/admin/auth/index", "/admin/auth/403", "/admin/index/*").permitAll()
                .antMatchers("/admin/**").authenticated()
                .and()
                .formLogin()
                .loginPage("/admin/auth/401")
                .defaultSuccessUrl("/admin/auth/index")
                .and()
                .exceptionHandling()
                .accessDeniedPage("/admin/auth/403")
                .and()
                .csrf().disable(); // Be cautious about disabling CSRF

        return http.build();
    }*/

    /*@Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }*/

   /* @Bean
    public AdminAuthenticationFilter adminAuthenticationFilter() throws Exception {
        AdminAuthenticationFilter filter = new AdminAuthenticationFilter();
        filter.(authenticationManagerBean());
        return filter;
    }*/

    /*@Bean
    public AdminAuthenticationFilter adminAuthenticationFilter() throws Exception {
        AdminAuthenticationFilter filter = new AdminAuthenticationFilter();
        filter.setAuthenticationManager(authenticationManagerBean());
        return filter;
    }*/
}
