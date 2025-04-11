package org.linlinjava.litemall.admin.service;

import org.linlinjava.litemall.db.service.LitemallAdminService;
import org.linlinjava.litemall.db.service.LitemallPermissionService;
import org.linlinjava.litemall.db.service.LitemallRoleService;
import org.springframework.stereotype.Service;

@Service
//public class AdminUserDetailsService implements UserDetailsService {
public class AdminUserDetailsService  {
    private final LitemallAdminService adminService;
    private final LitemallRoleService roleService;
    private final LitemallPermissionService permissionService;


    public AdminUserDetailsService(LitemallAdminService adminService, LitemallRoleService roleService, LitemallPermissionService permissionService) {
        this.adminService = adminService;
        this.roleService = roleService;
        this.permissionService = permissionService;
    }
    //@Override
    /*public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<LitemallAdmin> adminList = adminService.findAdmin(username);
        if(adminList.isEmpty()){
            throw new UsernameNotFoundException("Account information for user (" + username + ") not found");
        }if (adminList.size() > 1) {
            throw new IllegalStateException("Multiple accounts exist for the same username");
        }
        LitemallAdmin admin = adminList.get(0);
        Integer[] roleIds = admin.getRoleIds();
        Set<String> roles = roleService.queryByIds(roleIds);
        Set<String> permissions = permissionService.queryByRoleIds(roleIds);

        return new org.springframework.security.core.userdetails.User(
                admin.getUsername(),
                admin.getPassword(),
                roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).collect(Collectors.toList())
        );
    }*/
}
