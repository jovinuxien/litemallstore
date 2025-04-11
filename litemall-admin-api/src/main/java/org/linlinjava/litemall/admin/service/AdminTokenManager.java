package org.linlinjava.litemall.admin.service;


import org.linlinjava.litemall.db.domain.LitemallAdmin;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AdminTokenManager {

    private static final Map<String, LitemallAdmin> tokenMap = new ConcurrentHashMap<>();
    private static final Map<Integer, String> idMap = new ConcurrentHashMap<>();

   /* @Autowired
    private LitemallAdminService adminService;
    public String createToken(Integer adminId){
        String token = UUID.randomUUID().toString();
        LitemallAdmin admin = adminService.findById(adminId);
        tokenMap.put(token, admin);
        idMap.put(adminId, token);
        return token;
    }

    public void removeToken(Integer adminId) {
        String token = idMap.remove(adminId);
        tokenMap.remove(token);
    }*/

    /*public LitemallAdmin getAdminByToken(String token) {
        return tokenMap.get(token);
    }

    public Integer getAdminId(String token) {
        LitemallAdmin admin = tokenMap.get(token);
        return admin != null ? admin.getId() : null;
    }
    public boolean verifyToken(String token) {
        return tokenMap.containsKey(token);
    }*/

   /* public Collection<? extends GrantedAuthority> grantedAuthorities(String token){
        LitemallAdmin admin = getAdminByToken(token);
        if (admin != null) {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            // Add roles or permissions based on the admin
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            // You can add more roles or permissions here based on your application logic
            return authorities;
        }
        return new ArrayList<>();
    }*/


}
