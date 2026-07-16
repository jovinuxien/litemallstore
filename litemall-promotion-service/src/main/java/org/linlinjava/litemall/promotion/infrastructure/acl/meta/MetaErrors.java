package org.linlinjava.litemall.promotion.infrastructure.acl.meta;

/**
 * Error-message hygiene for the Meta ACL: Feign exception messages embed the
 * full request URL, and Graph API calls carry {@code access_token} as a query
 * param — without scrubbing, a failed publish would persist the page token
 * into the ledger's {@code error} column (and the admin UI). TikTok is not
 * affected (its token rides the Authorization header).
 */
final class MetaErrors {

    private MetaErrors() {
        // Utility class — no instances
    }

    /** Redact any access_token query-param value from an exception message. */
    static String sanitize(String message) {
        if (message == null) {
            return null;
        }
        return message.replaceAll("(access_token=)[^&\\s\\]]+", "$1***");
    }
}
