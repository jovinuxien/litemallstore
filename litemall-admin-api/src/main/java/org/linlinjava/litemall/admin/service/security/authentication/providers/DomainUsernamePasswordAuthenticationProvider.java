package org.linlinjava.litemall.admin.service.security.authentication.providers;

import org.linlinjava.litemall.admin.service.TokenService;
import org.linlinjava.litemall.admin.service.security.authentication.AuthenticationWithToken;
import org.linlinjava.litemall.admin.service.security.authentication.ExternalServiceAuthenticator;
import org.linlinjava.litemall.admin.util.UserType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import java.util.Optional;

public class DomainUsernamePasswordAuthenticationProvider implements AuthenticationProvider {


    private TokenService tokenService;
    private ExternalServiceAuthenticator externalServiceAuthenticator;

    public DomainUsernamePasswordAuthenticationProvider(TokenService tokenService, ExternalServiceAuthenticator externalServiceAuthenticator) {
        this.tokenService = tokenService;
        this.externalServiceAuthenticator = externalServiceAuthenticator;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = (String) authentication.getPrincipal();
        String password = (String) authentication.getCredentials();

        if(username.isEmpty() || password.isEmpty()){
            throw new BadCredentialsException("Invalid Domain User Credentials");
        }
        System.out.println("The username and password at the DomainUsernamePasswordAuthenticationProvider: " + username + " " + password);
        AuthenticationWithToken resultOfAuthentication = externalServiceAuthenticator.authenticate(username, password, UserType.USER);
        String newToken = tokenService.generateNewToken();
        resultOfAuthentication.setToken(newToken);
        tokenService.store(newToken, resultOfAuthentication);

        return resultOfAuthentication;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
