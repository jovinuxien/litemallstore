package org.linlinjava.litemall.admin.service.security.authentication;

import org.linlinjava.litemall.admin.util.UserType;

public interface ExternalServiceAuthenticator {
    AuthenticationWithToken authenticate(String username, String password, UserType userType);
}
