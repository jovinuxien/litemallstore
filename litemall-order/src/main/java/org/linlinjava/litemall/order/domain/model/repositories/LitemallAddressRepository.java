package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.db.domain.LitemallAddress;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallAddressId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallUserId;

import java.util.List;

public interface LitemallAddressRepository {


List<LitemallAddress> getListAddressesByUserId(LitemallUserId userId);

LitemallAddress findAddress(LitemallUserId userId, LitemallAddressId example);


int insertAddress(LitemallAddress address);

int updateAddress(LitemallAddress address);

int deleteAddress(LitemallAddressId addressId);

void resetDefaultAddress(LitemallUserId userId);

List<LitemallAddress> findAddresses(LitemallUserId userId, String name, Integer page, Integer limit, String sort, String order);

}
