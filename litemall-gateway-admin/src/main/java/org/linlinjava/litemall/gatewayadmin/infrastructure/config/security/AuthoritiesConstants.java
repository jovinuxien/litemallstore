package org.linlinjava.litemall.gatewayadmin.infrastructure.config.security;

public final class AuthoritiesConstants {

    public static final String ADMIN = "ROLE_ADMIN";

    /** Affiliate (promoter) portal realm — litemall_user with is_promoter=1, never litemall_admin. */
    public static final String AFFILIATE = "ROLE_AFFILIATE";

    public static final String USER = "ROLE_USER";

    public static final String ANONYMOUS = "ROLE_ANONYMOUS";

    private AuthoritiesConstants() {}
}
