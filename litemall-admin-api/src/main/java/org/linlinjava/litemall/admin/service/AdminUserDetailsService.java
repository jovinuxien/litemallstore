package org.linlinjava.litemall.admin.service;

import org.linlinjava.litemall.db.domain.LitemallAdmin;
import org.linlinjava.litemall.db.service.LitemallAdminService;
import org.linlinjava.litemall.db.service.LitemallPermissionService;
import org.linlinjava.litemall.db.service.LitemallRoleService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AdminUserDetailsService implements UserDetailsService {
    private final LitemallAdminService adminService;
    private final LitemallRoleService roleService;
    private final LitemallPermissionService permissionService;


    public AdminUserDetailsService(LitemallAdminService adminService, LitemallRoleService roleService, LitemallPermissionService permissionService) {
        this.adminService = adminService;
        this.roleService = roleService;
        this.permissionService = permissionService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<LitemallAdmin> admins = adminService.findAdmin(username);

        if (admins == null) {
            throw new UsernameNotFoundException(" Admins not found (" + username + ") not found");
        }

        LitemallAdmin admin = admins.get(0);
        Set<String> roles = roleService.queryByIds(admin.getRoleIds());
        Set<String> permissions = permissionService.queryByRoleIds(admin.getRoleIds());
        Set<GrantedAuthority> authorities = new HashSet<>();
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        permissions.forEach(perm -> authorities.add(new SimpleGrantedAuthority(perm)));

        return new User(
                admin.getUsername(),
                admin.getPassword(),
                true, true, true, true,
                authorities
        );
    }
}
