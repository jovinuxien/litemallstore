package com.joviste.application.config;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
@EnableAuthorizationServer
public class AuthorizationServerConfig extends AuthorizationServerConfigurerAdapter {
    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserDetailsService userDetailsService;

    private TokenStore tokenStore;
    private PasswordEncoder passwordEncoder;

    public AuthorizationServerConfig(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
       /* clients.inMemory()
                .withClient("clientid")
                .secret("{noop}secret") // {noop} prefix indicates plain text (for demo only)
                .authorizedGrantTypes("password", "refresh_token")
                .scopes("read", "write")
                .accessTokenValiditySeconds(3600)
                .refreshTokenValiditySeconds(86400);*/

        clients.inMemory()
                .withClient("litemallclient")
                .secret(passwordEncoder.encode("litemallclientsecret"))
                .authorizedGrantTypes("client_credentials", "password")
                .scopes("read", "write")
                .accessTokenValiditySeconds(3600)
                .resourceIds("sample-oauth")
                .and()
                .withClient("orderprocessingservice")
                .secret(passwordEncoder.encode("orderprocessingservicesecret"))
                .authorizedGrantTypes("client_credentials", "password")
                .scopes("read", "write")
                .accessTokenValiditySeconds(3600)
                .resourceIds("sample-oauth");
    }

    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) {
        endpoints
                .authenticationManager(authenticationManager)
                .approvalStoreDisabled()
                .tokenStore(tokenStore)
                .userDetailsService(userDetailsService);
    }

    @Override
    public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
        security
                .tokenKeyAccess("permitAll()")
                .checkTokenAccess("isAuthenticated()")
                .allowFormAuthenticationForClients();
    }


}
