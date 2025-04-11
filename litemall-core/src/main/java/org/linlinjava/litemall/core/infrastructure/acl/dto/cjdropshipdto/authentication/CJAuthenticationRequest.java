package org.linlinjava.litemall.core.infrastructure.acl.dto.cjdropshipdto.authentication;

import org.springframework.stereotype.Component;

@Component
public class CJAuthenticationRequest {

    private String email;
    private String cjApiKey;

    public CJAuthenticationRequest(String email, String cjApiKey) {
        this.email = email;
        this.cjApiKey = cjApiKey;
    }

    public CJAuthenticationRequest() {
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getCjApiKey() {
        return cjApiKey;
    }
    public void setCjApiKey(String cjApiKey) {
        this.cjApiKey = cjApiKey;
    }
}
