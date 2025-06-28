package org.linlinjava.litemall.goods.domain.model.repositories.user;

import org.linlinjava.litemall.goods.domain.model.agregates.user.LitemallAdminAggregate;
import org.linlinjava.litemall.goods.domain.model.agregates.user.LitemallUserAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.user.LitemallAdminId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.user.LitemallUserId;

import java.util.List;

public interface LitemallAdminRepository {

    LitemallAdminAggregate findById(LitemallAdminId adminId);

    void saveAdmin(LitemallAdminAggregate admin);
    int updateAdmin(LitemallAdminAggregate admin);

    void deleteById(LitemallAdminId adminId);
    int countUser();

    List<LitemallAdminAggregate> all();
}
