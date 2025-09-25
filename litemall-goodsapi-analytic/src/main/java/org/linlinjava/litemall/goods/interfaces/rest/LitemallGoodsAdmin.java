package org.linlinjava.litemall.goods.interfaces.rest;


import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.security.RolesAllowed;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("srv/admin")
public class LitemallGoodsAdmin {


    //@PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/goods/ping")
    //@RolesAllowed({"ADMIN"})
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    //public Object getPing(JwtAuthenticationToken auth) {
    public Object getPing(Principal principal) {
       /* return new UserInfoDto(
                auth.getToken().getClaimAsString(StandardClaimNames.PREFERRED_USERNAME));
                //auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
                //auth.getAuthorities().stream().map  // convert to list));*/
        Map<String, Object> data = new HashMap<>();
        data.put("hello", "world");
        data.put("principal username", principal.getName());
        return data;
    }
}
