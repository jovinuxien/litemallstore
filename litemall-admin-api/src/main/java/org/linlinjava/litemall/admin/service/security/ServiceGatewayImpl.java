package org.linlinjava.litemall.admin.service.security;


import org.linlinjava.litemall.admin.service.jovistuff.ServiceGateway;
import org.linlinjava.litemall.admin.service.jovistuff.domain.DomainUser;
import org.linlinjava.litemall.admin.service.jovistuff.domain.Stuff;
import org.linlinjava.litemall.admin.service.security.authentication.providers.AuthenticatedExternalServiceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ServiceGatewayImpl extends ServiceGatewayBase implements ServiceGateway {

    @Autowired
    public ServiceGatewayImpl(AuthenticatedExternalServiceProvider authenticatedExternalServiceProvider) {
        super(authenticatedExternalServiceProvider);
    }

    @Override
    public List<Stuff> getSomeStuff() {
        String stuffFromExternalService = externalWebServiceStub().getSomeStuff();
        return List.of(new Stuff("Stuff from external service", new DomainUser("Jovi"), "Details from external service")  );
    }

    @Override
    public void createStuff(Stuff newStuff, DomainUser domainUser) {
        System.out.println("Thanks you created your first stuff");
    }
}
