package org.linlinjava.litemall.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import org.linlinjava.litemall.admin.service.security.authentication.AuthenticationWithToken;
import org.linlinjava.litemall.admin.util.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

import javax.servlet.http.*;

public class AuthenticationFilter extends GenericFilterBean {


    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationFilter.class);
    public static final String TOKEN_SESSION_KEY = "token";
    public static final String USER_SESSION_KEY = "user";
    public static final String POST_ADMIN_URL_AUTHENTICATE = "/admin/auth/login";
    //public static final String POST_URL_AUTHENTICATE = "wx/auth/login";

    private AuthenticationManager authenticationManager;
    @Autowired
    private TokenService tokenService;

    public  AuthenticationFilter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = asHttp(request);
        HttpServletResponse httpResponse = asHttp(response);

        Optional<String> username = Optional.fromNullable(httpRequest.getHeader("X-Auth-Username"));
        Optional<String> password = Optional.fromNullable(httpRequest.getHeader("X-Auth-Password"));

        Optional<String> token = Optional.fromNullable(httpRequest.getHeader("X-Auth-Token"));

        String resourcePath = new UrlPathHelper().getPathWithinApplication(httpRequest);
        try {
            if(postToAuthenticate(httpRequest, resourcePath)) {
                LOGGER.debug("Try to authenticate user {} by X-Auth-Username method", username.get());
                System.out.println("the resource path is: " + resourcePath);
                System.out.println("the username and password are: " + username + " and " + password);
                processUsernameAndPasswordAuthenticate(httpResponse, username, password);
                return;
            }
            if(token.isPresent()) {
                LOGGER.debug("Try to authenticate user {} by token method", token);
                processTokenAuthenticate(token);
            }

            LOGGER.debug("Authentication is passing request down the filter chain");
            //System.out.println("the username and password are: " + username + " and " + password);
            addSessionContextToLogging();
            chain.doFilter(request, response);

        }catch (InternalAuthenticationServiceException iauthSrvException) {
            SecurityContextHolder.clearContext();
            LOGGER.error("Internal authentication service exception: {}", iauthSrvException.getMessage());
            httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication failed: " + iauthSrvException.getMessage());
        }catch(AuthenticationException authException) {
            SecurityContextHolder.clearContext();
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed: " + authException.getMessage());
        } finally {
            MDC.remove(TOKEN_SESSION_KEY);
            MDC.remove(USER_SESSION_KEY);
        }

    }

    private HttpServletRequest asHttp(ServletRequest request) {
        return (HttpServletRequest) request;
    }

    private HttpServletResponse asHttp(ServletResponse response) {
        return (HttpServletResponse) response;
    }

    private boolean postToAuthenticate(HttpServletRequest request, String resourcePath) {
        return POST_ADMIN_URL_AUTHENTICATE.equalsIgnoreCase(resourcePath) && request.getMethod().equals("POST");
    }

    private void addSessionContextToLogging() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String tokenValue ="EMPTY";
        if(authentication != null && !Strings.isNullOrEmpty(authentication.getDetails().toString())){
            MessageDigestPasswordEncoder encoder = new MessageDigestPasswordEncoder("SHA-1");
            tokenValue = encoder.encode(authentication.getDetails().toString());
        }
        MDC.put(TOKEN_SESSION_KEY, tokenValue);

        String userValue = "EMPTY";
        if(authentication != null && !Strings.isNullOrEmpty(authentication.getPrincipal().toString())){
            userValue = authentication.getPrincipal().toString();
        }
        MDC.put(USER_SESSION_KEY, userValue);
    }


    private void processUsernameAndPasswordAuthenticate(HttpServletResponse httpResponse, Optional<String> username, Optional<String> password) throws IOException {
        Authentication resultOfAuthenticate = tryToAuthenticateWithUsernameAndPassword(username, password);
        SecurityContextHolder.getContext().setAuthentication(resultOfAuthenticate);
        httpResponse.setStatus(HttpServletResponse.SC_OK);
        TokenResponse tokenResponse = new TokenResponse(resultOfAuthenticate.getDetails().toString());

        String tokenJsonResponse = new ObjectMapper().writeValueAsString(tokenResponse);
        httpResponse.addHeader("Content-Type", "application/json");
        httpResponse.getWriter().write(tokenJsonResponse);
    }



    private void processTokenAuthenticate(Optional<String> token){
        Authentication authentication = tryToAuthenticateWithToken(token);
       SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    
    
    private Authentication tryToAuthenticateWithUsernameAndPassword(Optional<String> username, Optional<String> password) {
        UsernamePasswordAuthenticationToken requestAuthentication = new UsernamePasswordAuthenticationToken(username, password);
        return tryToAuthenticate(requestAuthentication);
    }

    private Authentication tryToAuthenticateWithToken(Optional<String> token) {
        PreAuthenticatedAuthenticationToken requestAuthentication = new PreAuthenticatedAuthenticationToken(token, null);
        return tryToAuthenticate(requestAuthentication);
    }


    private Authentication tryToAuthenticate(Authentication requestAuthentication) {
         Authentication responseAuthentication = authenticationManager.authenticate(requestAuthentication);

         if(authenticationManager == null || !responseAuthentication.isAuthenticated()){
             throw new InternalAuthenticationServiceException("Unable to authentication Domain User for provided token");
         }
         logger.debug("User Successfully authenticated responseAuthentication.getPrincipal()");
         return responseAuthentication;
    }
}
