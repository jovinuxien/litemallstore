package org.linlinjava.litemall.gatewayadmin.auth;

/** Unknown, revoked or expired admin refresh token at /auth/refresh. */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
