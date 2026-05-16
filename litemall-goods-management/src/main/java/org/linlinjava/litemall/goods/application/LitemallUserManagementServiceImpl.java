package org.linlinjava.litemall.goods.application;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.goods.LitemallUserManagementService;
import org.linlinjava.litemall.goods.domain.model.agregates.user.LitemallAdminAggregate;
import org.linlinjava.litemall.goods.domain.model.agregates.user.LitemallUserAggregate;
import org.linlinjava.litemall.goods.domain.model.repositories.user.LitemallAdminRepository;
import org.linlinjava.litemall.goods.domain.model.repositories.user.LitemallPermissionRepository;
import org.linlinjava.litemall.goods.domain.model.repositories.user.LitemallRoleRepository;
import org.linlinjava.litemall.goods.domain.model.repositories.user.LitemallUserRepository;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.user.LitemallUserId;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;


@Service
public class LitemallUserManagementServiceImpl implements LitemallUserManagementService {


    private final LitemallUserRepository litemallUserRepository;
    private final LitemallPermissionRepository litemallPermissionRepository;
    private final LitemallRoleRepository litemallRoleRepository;
    private final LitemallAdminRepository litemallAdminRepository;


    public LitemallUserManagementServiceImpl(LitemallUserRepository userRepository,
                                             LitemallPermissionRepository permissionRepository,
                                             LitemallRoleRepository roleRepository, LitemallAdminRepository adminRepository) {
        this.litemallUserRepository = userRepository;
        this.litemallPermissionRepository = permissionRepository;
        this.litemallRoleRepository = roleRepository;
        this.litemallAdminRepository = adminRepository;

    }

    @Override
    public Object userDetail(Integer userId) {
        var id = new LitemallUserId(userId);

        LitemallUserAggregate userAggregate = litemallUserRepository.findById(id);
        return ResponseUtil.ok(userAggregate);
    }

    @Override
    public Object userDetailByUsername(String username) {
        if(username.equals("admin")){
            return ResponseUtil.ok(litemallAdminRepository.findByUsername(username));
        }
        return ResponseUtil.ok(litemallUserRepository.queryByUsername(username));
    }

    @Override
    public Object listUsers(String username, String mobile, Integer page, Integer limit, String sort, String order) {
        return ResponseUtil.okList(litemallUserRepository.querySelective(username, mobile, page, limit, sort, order));
    }

    @Override
    public Object updateUser(LitemallUserAggregate userAggregate) {
        return null;
    }

    @Override
    public Object saveUser(LitemallUserAggregate userAggregate) {
        Map<String, Object> result = new HashMap<>();
        if(litemallUserRepository.findById(userAggregate.getUserId()) == null){
            litemallUserRepository.saveUser(userAggregate);
            result.put("User Saved", userAggregate.getUsername());
        };
        //litemallUserRepository.saveUser(userAggregate);
        result.put("This user already exists", userAggregate.getUsername());
        return ResponseUtil.ok(result);
    }

    @Override
    public Object updateAdminUser(LitemallAdminAggregate adminAggregate) {
        return ResponseUtil.ok(litemallAdminRepository.updateAdmin(adminAggregate));
    }

    @Override
    public Object saveAdminUser(LitemallAdminAggregate adminAggregate) {
         litemallAdminRepository.saveAdmin(adminAggregate);
         return ResponseUtil.ok("Admin User Saved");
    }


    @Override
    public Object permissionByRoleId(Integer roleId) {
        var permissions = litemallPermissionRepository.queryByRoleId(roleId);
        return ResponseUtil.ok(permissions);
    }

    @Override
    public Object isSuperPermission(Integer roleId) {
        var isSuperPermission = litemallPermissionRepository.checkSuperPermission(roleId);
        return ResponseUtil.ok(isSuperPermission);
    }
}
