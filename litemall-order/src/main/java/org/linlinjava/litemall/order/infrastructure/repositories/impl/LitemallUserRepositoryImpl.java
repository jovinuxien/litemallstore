package org.linlinjava.litemall.order.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallUserRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallUserId;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public class LitemallUserRepositoryImpl implements LitemallUserRepository {
    @Override
    public LitemallUser findById(LitemallUserId id) {
        return null;
    }

    @Override
    public LitemallUser findByOid(String openId) {
        return null;
    }

    @Override
    public void addUser(LitemallUser user) {

    }

    @Override
    public void updateUser(LitemallUser user) {

    }

    @Override
    public int count() {
        return 0;
    }

    @Override
    public List<LitemallUser> queryByUsername(String username) {
        return List.of();
    }

    @Override
    public void deleteById(LitemallUserId id) {

    }
}
