package org.linlinjava.litemall.admin.service.jovistuff.domain;

import lombok.AllArgsConstructor;


public class DomainUser {

    private String username;

    public DomainUser(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public String toString() {
        return username;
    }
}
