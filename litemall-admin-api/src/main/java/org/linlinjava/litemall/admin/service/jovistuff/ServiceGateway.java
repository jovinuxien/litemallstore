package org.linlinjava.litemall.admin.service.jovistuff;

import org.linlinjava.litemall.admin.service.jovistuff.domain.DomainUser;
import org.linlinjava.litemall.admin.service.jovistuff.domain.Stuff;

import java.util.List;

public interface ServiceGateway {
    List<Stuff> getSomeStuff();

    void createStuff(Stuff newStuff, DomainUser domainUser);
}
