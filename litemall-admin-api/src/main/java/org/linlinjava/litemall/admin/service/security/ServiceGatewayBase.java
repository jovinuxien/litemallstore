package org.linlinjava.litemall.admin.service.security;

import org.linlinjava.litemall.admin.service.security.authentication.providers.AuthenticatedExternalServiceProvider;
import org.linlinjava.litemall.admin.service.security.externalservice.ExternalWebServiceStub;

public abstract class ServiceGatewayBase {

    private AuthenticatedExternalServiceProvider authenticatedExternalServiceProvider;

    public ServiceGatewayBase(AuthenticatedExternalServiceProvider authenticatedExternalServiceProvider) {
        this.authenticatedExternalServiceProvider = authenticatedExternalServiceProvider;
    }
    protected ExternalWebServiceStub externalWebServiceStub() {
        return authenticatedExternalServiceProvider.provide().getExternalWebServiceStub();
    }
}
