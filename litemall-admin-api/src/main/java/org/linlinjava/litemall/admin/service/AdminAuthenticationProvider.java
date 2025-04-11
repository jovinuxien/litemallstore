package org.linlinjava.litemall.admin.service;

import org.springframework.stereotype.Component;

@Component
public class AdminAuthenticationProvider {
//public class AdminAuthenticationProvider implements AuthenticationProvider {

    /*private final AdminUserDetailsService userDetailsService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AdminTokenManager adminTokenManager;*/

   /* public AdminAuthenticationProvider(AdminUserDetailsService userDetailsService, BCryptPasswordEncoder passwordEncoder, AdminTokenManager adminTokenManager) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.adminTokenManager = adminTokenManager;
    }*/

   /* @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        if(authentication instanceof AdminAuthenticationToken){
            String token = (String) authentication.getPrincipal();
            if (adminTokenManager.verifyToken(token)) {
                Collection<? extends GrantedAuthority> authorities = adminTokenManager.grantedAuthorities(token);
                return new AdminAuthenticationToken(token, null, authorities);
            }
            throw new BadCredentialsException("Invalid token");
        }
        // Existing username/password authentication logic
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (passwordEncoder.matches(password, userDetails.getPassword())) {
            return new UsernamePasswordAuthenticationToken(userDetails, password, userDetails.getAuthorities());
        } else {
            throw new BadCredentialsException("Invalid username or password");
        }
    }*/

   /* @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class) ||
                authentication.equals(AdminAuthenticationToken.class);
    }*/
}
