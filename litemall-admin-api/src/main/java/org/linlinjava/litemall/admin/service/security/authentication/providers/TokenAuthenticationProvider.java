package org.linlinjava.litemall.admin.service.security.authentication.providers;

import org.linlinjava.litemall.admin.service.TokenService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import java.util.Optional;

public class TokenAuthenticationProvider implements AuthenticationProvider {

    private TokenService tokenService;

    public TokenAuthenticationProvider(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
       Optional<String> token = (Optional) authentication.getPrincipal();

       if(!token.isPresent() || token.get().isEmpty()){
           throw new BadCredentialsException("Invalid token");
       }
        return tokenService.retrieve(token.get());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(PreAuthenticatedAuthenticationToken.class);
    }
}
