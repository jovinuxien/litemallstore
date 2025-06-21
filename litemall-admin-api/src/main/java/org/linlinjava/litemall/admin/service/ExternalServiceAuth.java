package org.linlinjava.litemall.admin.service;

import org.linlinjava.litemall.admin.service.security.AuthenticatedExternalWebService;
import org.linlinjava.litemall.admin.service.security.authentication.AuthenticationWithToken;
import org.linlinjava.litemall.admin.service.security.authentication.ExternalServiceAuthenticator;
import org.linlinjava.litemall.admin.util.UserType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Service;

@Service
public class ExternalServiceAuth {

    @Autowired
    private ExternalServiceAuthenticator externalServiceAuthenticator;

    @Autowired
    private TokenService tokenService;

    public String authenticateUser(String username, String password) {
        return authenticate(username, password, UserType.USER);
    }
    public String authenticateAdmin(String username, String password) {
        return authenticate(username, password, UserType.ADMIN);
    }

    private String authenticate(String username, String password, UserType userType) {
        /* AuthenticatedExternalWebService authenticatedExternalWebService = new AuthenticatedExternalWebService(username, null,
                AuthorityUtils.commaSeparatedStringToAuthorityList("ROLE_DOMAIN_USER"));*/
        // 1. Authenticate using the external authenticator
        AuthenticationWithToken authentication = externalServiceAuthenticator.authenticate(username, password, userType);

        // 2. Generate and store the token through tokenService
        String token = tokenService.generateNewToken();
        authentication.setToken(token);
        tokenService.store(token, authentication);
        return token;
    }
}
