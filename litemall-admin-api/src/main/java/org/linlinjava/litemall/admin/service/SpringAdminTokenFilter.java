package org.linlinjava.litemall.admin.service;


import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class SpringAdminTokenFilter extends GenericFilterBean {

    public static final String LOGIN_ADMIN_TOKEN = "X-Litemall-Admin-Token";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        //HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        String token = httpServletRequest.getHeader(LOGIN_ADMIN_TOKEN);
        if(!StringUtils.hasText(token)){
            SecurityContextHolder.getContext().setAuthentication(
                    new PreAuthenticatedAuthenticationToken(token, null)
            );
        }
        chain.doFilter(request, response);
    }
}
