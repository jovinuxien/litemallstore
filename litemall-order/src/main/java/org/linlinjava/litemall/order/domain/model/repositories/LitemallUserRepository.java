package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallUserId;

import java.util.List;

public interface LitemallUserRepository {


    LitemallUser findById(LitemallUserId id);

    LitemallUser findByOid(String openId);

    void addUser(LitemallUser user);

    void updateUser(LitemallUser user);

    int count();

    List<LitemallUser> queryByUsername(String username);

    void deleteById(LitemallUserId id);
}
