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
     * @throws IllegalArgumentException if a required field is blank, the postal
     *         code is too long, or an update targets an address the user does not own
     */
    @Transactional
    public Integer save(LitemallUserId userId, LitemallAddressAggregate address) {
        // Authoritative owner is the header user, never anything in the payload.
        address.setUserId(userId);
        normalize(address);

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
     * Reject unusable payloads with a message the customer can act on, and fill
     * the columns litemall_address declares NOT NULL without a default
     * (province/city/county) the way the storefront form intends: the region is
     * mandatory, the kommune/county is optional and falls back to the region /
     * empty string. Without this, a missing county surfaced as a raw errno-502
     * "System internal error" at checkout.
     */
    private void normalize(LitemallAddressAggregate address) {
        if (isBlank(address.getName())) {
            throw new IllegalArgumentException("Recipient name is required");
        }
        if (isBlank(address.getProvince())) {
            throw new IllegalArgumentException("Region is required");
        }
        if (isBlank(address.getAddressDetail())) {
            throw new IllegalArgumentException("Street address is required");
        }
        address.setName(address.getName().trim());
        address.setProvince(address.getProvince().trim());
        address.setAddressDetail(address.getAddressDetail().trim());
        address.setCity(isBlank(address.getCity()) ? address.getProvince() : address.getCity().trim());
        address.setCounty(isBlank(address.getCounty()) ? "" : address.getCounty().trim());
        address.setTel(address.getTel() == null ? "" : address.getTel().trim());
        if (address.getPostalCode() != null) {
            String postalCode = address.getPostalCode().trim();
            if (postalCode.length() > 20) {
                throw new IllegalArgumentException("Postal code is too long (max 20 characters)");
            }
            address.setPostalCode(postalCode);
        }
        if (address.getAreaCode() != null) {
            String areaCode = address.getAreaCode().trim();
            if (areaCode.length() > 20) {
                throw new IllegalArgumentException("Area code is too long (max 20 characters)");
            }
            address.setAreaCode(areaCode);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
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
