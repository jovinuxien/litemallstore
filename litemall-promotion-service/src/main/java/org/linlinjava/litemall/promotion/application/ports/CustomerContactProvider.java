package org.linlinjava.litemall.promotion.application.ports;

import java.util.Optional;

/**
 * Inbound port resolving a litemall user id to their contact identity (email +
 * nickname) for outbound marketing delivery (Wave-6 Mautic enrichment).
 * Implemented in {@code infrastructure} over the shared litemall-db user read
 * model; the Mautic ACL depends on this port, never on the db module.
 */
public interface CustomerContactProvider {

    /** Empty when the user does not exist; {@code email} may be null/blank (never subscribed one). */
    Optional<CustomerContact> contactOf(Integer userId);

    record CustomerContact(Integer userId, String email, String nickname) {

        public boolean hasEmail() {
            return email != null && !email.isBlank();
        }
    }
}
