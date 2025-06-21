package org.linlinjava.litemall.admin.config;

import org.linlinjava.litemall.admin.service.AdminAuthenticationProvider;
import org.linlinjava.litemall.admin.service.AuthenticationFilter;
import org.linlinjava.litemall.admin.service.TokenService;
import org.linlinjava.litemall.admin.service.security.AuthoritiesConstants;
import org.linlinjava.litemall.admin.service.security.authentication.ExternalServiceAuthenticator;
import org.linlinjava.litemall.admin.service.security.authentication.providers.DomainUsernamePasswordAuthenticationProvider;
import org.linlinjava.litemall.admin.service.security.authentication.providers.TokenAuthenticationProvider;
import org.linlinjava.litemall.admin.service.security.externalservice.SomeExternalServiceAuthentication;
import org.linlinjava.litemall.admin.util.TokenStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.zalando.problem.spring.web.advice.security.SecurityProblemSupport;
import tech.jhipster.config.JHipsterProperties;

import javax.servlet.http.HttpServletResponse;


@Configuration
@EnableWebSecurity
@EnableScheduling
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
@Import(SecurityProblemSupport.class)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final JHipsterProperties jHipsterProperties;
    private final SecurityProblemSupport secProblemSupport;
    private final AdminAuthenticationProvider adminAuthenticationProvider;
    private final TokenStore tokenStore;

    String[] permitted = new String[]{

            "/wx/**",

            "/admin/auth/kaptcha",
            "/admin/auth/login",
            "/admin/auth/authenticate",
            "/admin/auth/401",
            "/admin/auth/index",
            "/admin/auth/403"

    };

    public  SecurityConfig(JHipsterProperties jHipsterProperties,
                           SecurityProblemSupport secProblemSupport,
                           AdminAuthenticationProvider adminAuthenticationProvider,
                           TokenStore tokenStore){
        this.jHipsterProperties = jHipsterProperties;
        this.secProblemSupport = secProblemSupport;
        this.adminAuthenticationProvider = adminAuthenticationProvider;
        this.tokenStore = tokenStore;

    }



    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }




    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                //s.addFilterBefore((Filter) new SpringAdminTokenFilter(), )
                //.exceptionHandling()
                //.authenticationEntryPoint(secProblemSupport)
                //.accessDeniedHandler(secProblemSupport)
                //.and()
                .headers()
                .contentSecurityPolicy(jHipsterProperties.getSecurity().getContentSecurityPolicy())
                .and()
                .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                .and()
                .permissionsPolicy().policy("camera=(), fullscreen=(self), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()")
                .and()
                .frameOptions()
                .sameOrigin()

                .and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()

                //.httpBasic(Customizer.withDefaults())
                .authorizeRequests()
                .antMatchers(permitted).permitAll()
                //.antMatchers("/admin/auth/**").permitAll()
                //.antMatchers("/admin/index/**").permitAll()
                .antMatchers("admin/**").hasRole(AuthoritiesConstants.ADMIN)
                .anyRequest().authenticated()

                .and()
                .exceptionHandling()
                .authenticationEntryPoint(unAuthorizedEntryPoint());

        //.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));

        http.addFilterBefore(new AuthenticationFilter(authenticationManager()), BasicAuthenticationFilter.class);

        //http.httpBasic();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(domainUsernamePasswordAuthenticationProvider()).
                authenticationProvider(tokenAuthenticationProvider());
    }

   /* @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf()
                .disable()
                //s.addFilterBefore((Filter) new SpringAdminTokenFilter(), )
                .exceptionHandling()
                    .authenticationEntryPoint(secProblemSupport)
                    .accessDeniedHandler(secProblemSupport)
                .and()
                    .headers()
                    .contentSecurityPolicy(jHipsterProperties.getSecurity().getContentSecurityPolicy())
                .and()
                    .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                .and()
                    .permissionsPolicy().policy("camera=(), fullscreen=(self), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()")
                .and()
                    .frameOptions()
                    .sameOrigin()

                .and()
                    .sessionManagement()
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)

                .and()
                *//*.authorizeHttpRequests(auth -> auth
                        //.antMatchers(permitted).permitAll()
                        .antMatchers("/admin/auth/**").permitAll()
                        .antMatchers("/admin/index/**").permitAll()
                        .antMatchers("admin/**").hasRole("admin")
                )*//*
                .httpBasic(Customizer.withDefaults())
                .authorizeRequests()
                    .antMatchers(permitted).permitAll()
                    .antMatchers("/admin/auth/**").permitAll()
                    .antMatchers("/admin/index/**").permitAll()
                    .antMatchers("admin/**").hasRole("admin")
                .and()
                    .exceptionHandling()
                .authenticationEntryPoint(unAuthorizedEntryPoint());
                    //.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));

        http.addFilterBefore(new AuthenticationFilter(authenticationManager()), BasicAuthenticationFilter.class);
        return http.build();
    }*/

    @Bean
    public ExternalServiceAuthenticator someExternalServiceAuthentication() {
        return new SomeExternalServiceAuthentication(); // TODO: Implement your own authentication logic here.
    }

    @Bean
    public AuthenticationProvider domainUsernamePasswordAuthenticationProvider() {
        return new DomainUsernamePasswordAuthenticationProvider(tokenService(), someExternalServiceAuthentication());
    }

    @Bean
    public AuthenticationProvider tokenAuthenticationProvider() {
        return new TokenAuthenticationProvider(tokenService());
    }

    @Bean
    public UserDetailsService userServiceDetailService() {
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();

        UserDetails user = User.withUsername("jovi")
                .password("1234")
                .roles(AuthoritiesConstants.ADMIN)
                .build();

        manager.createUser(user);

        return manager;
    }

    /*public ExternalServiceAuthenticator someExternalServiceAuthenticator() {
        return new
    }*/

    @Bean
    public TokenService tokenService() {
        return new TokenService();
    }

    @Bean
    public AuthenticationEntryPoint unAuthorizedEntryPoint() {
        return (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized" + authException.getMessage() );
    }
}
