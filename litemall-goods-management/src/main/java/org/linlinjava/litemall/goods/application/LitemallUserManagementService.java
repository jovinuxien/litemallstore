package org.linlinjava.litemall.goods.application;

import org.linlinjava.litemall.goods.domain.model.agregates.user.LitemallAdminAggregate;
import org.linlinjava.litemall.goods.domain.model.agregates.user.LitemallUserAggregate;

public interface LitemallUserManagementService {

    Object userDetail(Integer  userId);
    Object listUsers(String username, String mobile, Integer page, Integer limit, String sort, String order);

    Object updateUser(LitemallUserAggregate userAggregate);
    Object saveUser(LitemallUserAggregate userAggregate);


    Object updateAdminUser(LitemallAdminAggregate adminAggregate);
    Object saveAdminUser(LitemallAdminAggregate adminAggregate);

    Object permissionByRoleId(Integer roleId);
    Object isSuperPermission(Integer roleId);

}
