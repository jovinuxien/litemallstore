package org.linlinjava.litemall.goods.domain.model.repositories.user;

import org.linlinjava.litemall.goods.domain.model.agregates.user.LitemallAdminAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.user.LitemallAdminId;

import java.util.List;

public interface LitemallAdminRepository {

    LitemallAdminAggregate findById(LitemallAdminId adminId);
    LitemallAdminAggregate findByUsername(String  username);

    void saveAdmin(LitemallAdminAggregate admin);
    int updateAdmin(LitemallAdminAggregate admin);

    void deleteById(LitemallAdminId adminId);
    int countUser();

    List<LitemallAdminAggregate> all();
}
