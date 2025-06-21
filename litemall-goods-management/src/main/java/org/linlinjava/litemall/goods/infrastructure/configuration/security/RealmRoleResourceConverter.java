package org.linlinjava.litemall.goods.infrastructure.configuration.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class RealmRoleResourceConverter implements Converter<Jwt, Collection<GrantedAuthority>> {


    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Collection<GrantedAuthority> realmAuthorities = new ArrayList<>();
        Collection<GrantedAuthority> resourceAuthorities = new ArrayList<>();
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        // 1. collect realm roles
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            List<String> realmRoles = (List<String>) realmAccess.get("roles");
            realmRoles.forEach(roleName -> realmAuthorities.add(new SimpleGrantedAuthority(roleName)));
        }
        log.info("Realm authorities: {}", realmAuthorities);
        // 2. collect client roles
        /*Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null && resourceAccess.containsKey("account")) {
            Map<String, Object> clientRoles = (Map<String, Object>) resourceAccess.get("account");
            if (clientRoles.containsKey("roles")) {
                List<String> roles = (List<String>) clientRoles.get("roles");
                roles.forEach(roleName -> resourceAuthorities.add(new SimpleGrantedAuthority(roleName)));
            }
        }*/

        // Extract client roles (from all clients)
        Map<String, Map<String, Collection<String>>> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null) {
            resourceAccess.forEach((clientName, clientRoles) -> {
                if (clientRoles.containsKey("roles")) {
                    authorities.addAll(
                            clientRoles.get("roles").stream()
                                    .map(role -> new SimpleGrantedAuthority(clientName + "." + role))
                                    .collect(Collectors.toSet())
                    );
                }
            });
        }

        log.info("Resource authorities: {}", resourceAuthorities);
        authorities.addAll(realmAuthorities);
        authorities.addAll(resourceAuthorities);
        return authorities;
    }
}
