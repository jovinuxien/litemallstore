package org.linlinjava.litemall.admin.service.security.authentication.providers;


import org.linlinjava.litemall.admin.service.security.AuthenticatedExternalWebService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedExternalServiceProvider {
    public AuthenticatedExternalWebService provide(){
        return (AuthenticatedExternalWebService) SecurityContextHolder.getContext().getAuthentication();
    }
}
