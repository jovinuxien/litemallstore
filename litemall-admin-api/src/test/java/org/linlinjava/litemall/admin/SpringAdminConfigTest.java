package org.linlinjava.litemall.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration
public class SpringAdminConfigTest {

    @PreAuthorize("authenticated")
    public String getMessage (){
        //Authentication authentication = SecurityContextHolder.getContext(.getAuthentication();)
      return "Hello, "; //+ authentication;
    }

    @Test()
    public void getMessageUnauthenticated() {
        getMessage();
    }

}
