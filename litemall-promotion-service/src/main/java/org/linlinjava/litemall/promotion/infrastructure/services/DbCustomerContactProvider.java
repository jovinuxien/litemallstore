package org.linlinjava.litemall.promotion.infrastructure.services;

import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.linlinjava.litemall.promotion.application.ports.CustomerContactProvider;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * {@link CustomerContactProvider} over the shared litemall-db user read model.
 * Email is whatever the user registered/verified through the account
 * self-service flows (Wave 4/5); a user without one yields a contact whose
 * {@code hasEmail()} is false — the Mautic delivery skips those (documented in
 * the Wave-6 handoff), it never invents addresses.
 */
@Service
public class DbCustomerContactProvider implements CustomerContactProvider {

    private final LitemallUserService userService;

    public DbCustomerContactProvider(LitemallUserService userService) {
        this.userService = userService;
    }

    @Override
    public Optional<CustomerContact> contactOf(Integer userId) {
        if (userId == null || userId <= 0) {
            return Optional.empty();
        }
        LitemallUser user = userService.findById(userId);
        if (user == null) {
            return Optional.empty();
        }
        return Optional.of(new CustomerContact(userId, user.getEmail(), user.getNickname()));
    }
}
