package org.linlinjava.litemall.goods.domain.model.repositories.user;

import org.linlinjava.litemall.goods.domain.model.agregates.user.LitemallUserAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.user.LitemallUserId;

import java.util.List;

public interface LitemallUserRepository {


    LitemallUserAggregate findById(LitemallUserId userId);
    List<LitemallUserAggregate> queryByUsername(String username);

    List<LitemallUserAggregate> querySelective(String username, String mobile, Integer page, Integer size, String sort, String order);

    void saveUser(LitemallUserAggregate user);
    void deleteById(LitemallUserId userId);
    int countUser();

}
