package org.linlinjava.litemall.goods.infrastructure.configuration.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        final Map<String, List<String>> realmAccess = (Map<String, List<String>>) jwt.getClaims().get("realm_access");
        if(realmAccess == null) {
            return Collections.emptyList();  // No roles found in the JWT.
        }
        Collection<String> roles = (Collection<String>) realmAccess.get("roles");
        System.out.println("Realm access role: " + roles);
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))  // prefix required by Spring Security for roles.)
                .collect(Collectors.toList());
    }
}
