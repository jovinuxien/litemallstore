package org.linlinjava.litemall.db.auth;

/**
 * Thrown when a token fails signature, issuer, audience or expiry validation.
 * Unchecked so edge security filters can map it straight to a 401.
 */
public class InvalidJwtException extends RuntimeException {

    public InvalidJwtException(String message, Throwable cause) {
        super(message, cause);
    }
}
