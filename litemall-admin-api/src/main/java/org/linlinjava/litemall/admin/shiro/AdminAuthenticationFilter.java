package org.linlinjava.litemall.admin.shiro;

public class AdminAuthenticationFilter {
//public class AdminAuthenticationFilter extends OncePerRequestFilter {

    private static final String LOGIN_ADMIN_TOKEN = "X-Litemall-Admin-Token";

    /*@Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader(LOGIN_ADMIN_TOKEN);
        if(token != null && !token.isEmpty()){
            AdminAuthenticationToken authentication = new AdminAuthenticationToken(token, null);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }*/
}
