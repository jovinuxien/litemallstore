package org.linlinjava.litemall.admin.service.security;

import org.linlinjava.litemall.admin.service.security.authentication.AuthenticationWithToken;
import org.linlinjava.litemall.admin.service.security.externalservice.ExternalWebServiceStub;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class AuthenticatedExternalWebService extends AuthenticationWithToken {

    private ExternalWebServiceStub externalWebServiceStub;
    private String token;

    public AuthenticatedExternalWebService(Object aPrincipal, Object aCredentials) {
        super(aPrincipal, aCredentials);
    }

    public AuthenticatedExternalWebService(Object aPrincipal, Object aCredentials, Collection<? extends GrantedAuthority> anAuthorities) {
        super(aPrincipal, aCredentials, anAuthorities);
    }

    public ExternalWebServiceStub getExternalWebServiceStub() {
        return externalWebServiceStub;
    }

    public void setExternalWebServiceStub(ExternalWebServiceStub externalWebServiceStub) {
        this.externalWebServiceStub = externalWebServiceStub;
    }


    @Override
    public void setToken(String token) {
        super.setToken(token);
    }

    @Override
    public String getToken() {
        return super.getToken();
    }
}
