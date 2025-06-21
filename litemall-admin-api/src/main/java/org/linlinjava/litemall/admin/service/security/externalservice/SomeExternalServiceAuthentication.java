package org.linlinjava.litemall.admin.service.security.externalservice;

import org.checkerframework.checker.units.qual.A;
import org.linlinjava.litemall.admin.service.security.AuthenticatedExternalWebService;
import org.linlinjava.litemall.admin.service.security.authentication.AuthenticationWithToken;
import org.linlinjava.litemall.admin.service.security.authentication.ExternalServiceAuthenticator;
import org.linlinjava.litemall.admin.service.jovistuff.domain.DomainUser;
import org.linlinjava.litemall.admin.util.UserType;
import org.linlinjava.litemall.core.util.bcrypt.BCryptPasswordEncoder;
import org.linlinjava.litemall.db.domain.LitemallAdmin;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.service.LitemallAdminService;
import org.linlinjava.litemall.db.service.LitemallPermissionService;
import org.linlinjava.litemall.db.service.LitemallRoleService;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SomeExternalServiceAuthentication implements ExternalServiceAuthenticator {

    @Autowired
    private LitemallAdminService adminService;
    @Autowired
    private LitemallUserService userService;
    @Autowired
    private LitemallRoleService roleService;
    @Autowired
    private LitemallPermissionService permissionService;
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;



    @Override
    public AuthenticationWithToken authenticate(String username, String password, UserType userType)  {
         Object principal;
         Set<String> roles = new HashSet<>();
         Set<String> permissions = new HashSet<>();

         if(userType == UserType.ADMIN) {
           // Admin logic
             // 1. Validate credentials against the database
             List<LitemallAdmin> adminList = adminService.findAdmin(username);
             if (adminList == null || adminList.isEmpty()) {
                 throw new RuntimeException("Admin not found");
             }
             LitemallAdmin admin = adminList.get(0);
             if (!passwordEncoder.matches(password, admin.getPassword())) {
                 throw new RuntimeException("Invalid Admin password");
             }

             principal = admin;
             Integer[] roleIds = admin.getRoleIds();
             roles = roleService.queryByIds(roleIds);
             permissions = permissionService.queryByRoleIds(roleIds);
         } else{
             // User logic
             // 1. Validate credentials against the database
             List<LitemallUser> userList = userService.queryByUsername(username);
             if (userList == null || userList.isEmpty()) {
                 throw new RuntimeException("User not found");
             }
             LitemallUser user = userList.get(0);
             if (!passwordEncoder.matches(password, user.getPassword())) {
                 throw new RuntimeException("Invalid User password");
             }

             principal = user;
             roles = Set.of("ROLE_USER");
             permissions = Set.of();
         }

         // Combine authorities
        List<GrantedAuthority> authorities = Stream.concat(
                roles.stream()
                        .map(role -> new SimpleGrantedAuthority(role)),
                permissions.stream().map(SimpleGrantedAuthority::new)
        ).collect(Collectors.toList());


        System.out.println("the authorities are: " + authorities);

        ExternalWebServiceStub externalWebService = new ExternalWebServiceStub();

        // Do all authentication mechanisms required by external web service protocol and validated response.
        // Throw descendant of Spring AuthenticationException in case of unsucessful authentication. For example BadCredentialsException

        // ...
        // ...

        // If authentication to external service succeeded then create authenticated wrapper with proper Principal and GrantedAuthorities.
        // GrantedAuthorities may come from external service authentication or be hardcoded at our layer as they are here with ROLE_DOMAIN_USER
        /*AuthenticatedExternalWebService authenticatedExternalWebService = new AuthenticatedExternalWebService(new DomainUser(username), null,
                AuthorityUtils.commaSeparatedStringToAuthorityList("ROLE_DOMAIN_USER"));*/

        return new AuthenticationWithToken(principal, password, authorities);
    }
}
