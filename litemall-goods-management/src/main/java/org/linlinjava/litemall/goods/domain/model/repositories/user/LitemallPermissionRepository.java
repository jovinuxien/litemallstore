package org.linlinjava.litemall.goods.domain.model.repositories.user;

import org.linlinjava.litemall.goods.domain.model.agregates.user.LitemallPermissionAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.user.LitemallRoleId;

import java.util.List;
import java.util.Set;

public interface LitemallPermissionRepository {


    Set<String> queryByRoleIds(Integer[] roleIds);
    Set<String> queryByRoleId(Integer roleId);

    boolean checkSuperPermission(Integer roleId);
    boolean checkSuperPermission(List<Integer> roleIds);



    void savePermission(LitemallPermissionAggregate permission);
    void deleteByRoleId(LitemallRoleId roleId);
}
