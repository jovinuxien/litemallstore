package org.linlinjava.litemall.goods.domain.model.repositories.user;

import org.linlinjava.litemall.goods.domain.model.agregates.user.LitemallRoleAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.user.LitemallRoleId;

public interface LitemallRoleRepository {


    LitemallRoleAggregate findById(LitemallRoleId roleId);

    void saveRole(LitemallRoleAggregate role);
    void deleteById(LitemallRoleId roleId);
}
