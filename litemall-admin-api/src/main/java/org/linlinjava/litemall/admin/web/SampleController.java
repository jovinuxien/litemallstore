package org.linlinjava.litemall.admin.web;


import org.linlinjava.litemall.admin.service.jovistuff.ServiceGateway;
import org.linlinjava.litemall.admin.service.jovistuff.domain.CurrentlyLoggedUser;
import org.linlinjava.litemall.admin.service.jovistuff.domain.DomainUser;
import org.linlinjava.litemall.admin.service.jovistuff.domain.Stuff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.linlinjava.litemall.admin.service.jovistuff.ApiController.STUFF_URL;

@RestController
@PreAuthorize("hasAuthority('ROLE_DOMAIN_USER')")
public class SampleController {
    private final ServiceGateway serviceGateway;

    @Autowired
    public SampleController(ServiceGateway serviceGateway) {
        this.serviceGateway = serviceGateway;
    }

    @RequestMapping(value = STUFF_URL, method = RequestMethod.GET)
    public List<Stuff> getSomeStuff() {
        return serviceGateway.getSomeStuff();
    }

    @RequestMapping(value = STUFF_URL, method = RequestMethod.POST)
    public void createStuff(@RequestBody Stuff newStuff, @CurrentlyLoggedUser DomainUser domainUser) {
        serviceGateway.createStuff(newStuff, domainUser);
    }
}
