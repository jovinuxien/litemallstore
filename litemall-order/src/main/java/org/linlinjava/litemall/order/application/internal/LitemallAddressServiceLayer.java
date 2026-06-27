package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallAddressRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallAddressId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Customer address-book application service. Every operation is scoped to the
 * acting {@link LitemallUserId} (bound from the gateway {@code X-User-Id} header by
 * the controller), so a caller can only ever read/modify their own addresses —
 * the same identity discipline as the order/wallet edges.
 */
@Service
public class LitemallAddressServiceLayer {

    private final LitemallAddressRepository addressRepository;

    public LitemallAddressServiceLayer(LitemallAddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public List<LitemallAddressAggregate> list(LitemallUserId userId) {
        return addressRepository.getListAddressesByUserId(userId);
    }

    /**
     * Fetch one address owned by the user, or {@code null} if it does not exist
     * (or belongs to someone else — the lookup is scoped by user).
     */
    @Transactional(readOnly = true)
    public LitemallAddressAggregate detail(LitemallUserId userId, LitemallAddressId addressId) {
        return addressRepository.findAddress(userId, addressId);
    }

    /**
     * Create (no id) or update (id present) an address for the user. When the
     * payload is the new default, the user's existing default is cleared first so
     * at most one address is ever the default. Returns the persisted id (the SPA
     * passes it straight to {@code /srv/order/submit}).
     *
     * @throws IllegalArgumentException if updating an address the user does not own
     */
    @Transactional
    public Integer save(LitemallUserId userId, LitemallAddressAggregate address) {
        // Authoritative owner is the header user, never anything in the payload.
        address.setUserId(userId);

        if (Boolean.TRUE.equals(address.getIsDefault())) {
            addressRepository.resetDefaultAddress(userId);
        }

        if (address.getAddressId() != null) {
            // Ownership check: the address must already exist and belong to the user.
            LitemallAddressAggregate existing = addressRepository.findAddress(userId, address.getAddressId());
            if (existing == null) {
                throw new IllegalArgumentException("Address not found for this user");
            }
            addressRepository.updateAddress(address);
            return address.getAddressId().getId();
        }

        addressRepository.insertAddress(address);
        return address.getAddressId() != null ? address.getAddressId().getId() : null;
    }

    /**
     * Soft-delete an address owned by the user.
     *
     * @throws IllegalArgumentException if the address is not owned by the user
     */
    @Transactional
    public void delete(LitemallUserId userId, LitemallAddressId addressId) {
        LitemallAddressAggregate existing = addressRepository.findAddress(userId, addressId);
        if (existing == null) {
            throw new IllegalArgumentException("Address not found for this user");
        }
        addressRepository.deleteAddress(addressId);
    }
}
