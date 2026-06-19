package org.linlinjava.litemall.order.domain.model.repositories;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallAddressId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

import java.util.List;

public interface LitemallAddressRepository {

List<LitemallAddressAggregate> getListAddressesByUserId(LitemallUserId userId);
LitemallAddressAggregate findAddress(LitemallUserId userId, LitemallAddressId example);

/**
 * The user's default shipping address, or {@code null} if they have none. Used as the
 * fallback when a place-order command carries no explicit addressId (the customer SPA has
 * no address picker yet — see the /srv/address follow-up).
 */
LitemallAddressAggregate findDefaultAddress(LitemallUserId userId);


int insertAddress(LitemallAddressAggregate address);

int updateAddress(LitemallAddressAggregate address);

int deleteAddress(LitemallAddressId addressId);

void resetDefaultAddress(LitemallUserId userId);

List<LitemallAddressAggregate> findAddresses(LitemallUserId userId, String name, Integer page, Integer limit, String sort, String order);

}
