package org.linlinjava.litemall.gatewayapi.auth;

/** Unknown, revoked or expired refresh token presented at /auth/refresh. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}