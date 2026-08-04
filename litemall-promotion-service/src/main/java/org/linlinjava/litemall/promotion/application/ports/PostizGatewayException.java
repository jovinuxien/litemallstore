package org.linlinjava.litemall.promotion.application.ports;

/**
 * A failed {@link PostizPort} call. {@code provider} is non-null only for
 * Postiz's structured validation 400s ({@code {provider, name, message}} from
 * its PostValidationExceptionFilter) — the message is Postiz's own wording,
 * surfaced verbatim per the Wave-17 contract, never wrapped.
 */
public class PostizGatewayException extends RuntimeException {

    /** Provider identifier the validation error names (e.g. {@code facebook}); null for transport errors. */
    private final String provider;

    /** HTTP status of the Postiz response, or 0 when the call never got one. */
    private final int status;

    public PostizGatewayException(String message, String provider, int status, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.status = status;
    }

    public String getProvider() {
        return provider;
    }

    public int getStatus() {
        return status;
    }
}
