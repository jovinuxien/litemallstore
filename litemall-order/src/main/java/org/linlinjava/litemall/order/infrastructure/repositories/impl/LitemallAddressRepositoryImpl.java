package org.linlinjava.litemall.order.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallAddressMapper;
import org.linlinjava.litemall.db.domain.LitemallAddress;
import org.linlinjava.litemall.db.domain.LitemallAddressExample;
import org.linlinjava.litemall.db.domain.LitemallCouponUser;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCouponUserAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallAddressRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallCouponUserStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class LitemallAddressRepositoryImpl implements LitemallAddressRepository {

    private final LitemallAddressMapper addressMapper;
    
    public LitemallAddressRepositoryImpl(LitemallAddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    @Override
    public List<LitemallAddressAggregate> getListAddressesByUserId(LitemallUserId userId) {
        return List.of();
    }

    @Override
    public LitemallAddressAggregate findAddress(LitemallUserId userId, LitemallAddressId addressId) {
       LitemallAddressExample example = new LitemallAddressExample();
       example.or().andUserIdEqualTo(userId.getId()).andIdEqualTo(addressId.getId()).andDeletedEqualTo(false);
       return convertToDomainModel(addressMapper.selectOneByExample(example));
    }

    @Override
    public int insertAddress(LitemallAddressAggregate address) {
        LitemallAddress litemallAddress =  convertToDataModel(address);
        litemallAddress.setAddTime(LocalDateTime.now());
        litemallAddress.setUpdateTime(LocalDateTime.now());
        return addressMapper.insertSelective(litemallAddress);
    }

    @Override
    public int updateAddress(LitemallAddressAggregate addressAggregate) {
        LitemallAddress address = convertToDataModel(addressAggregate);
        address.setUpdateTime(LocalDateTime.now());
        return addressMapper.updateByPrimaryKeySelective(address);
    }

    @Override
    public int deleteAddress(LitemallAddressId addressId) {
        return 0;
    }

    @Override
    public void resetDefaultAddress(LitemallUserId userId) {

    }

    @Override
    public List<LitemallAddressAggregate> findAddresses(LitemallUserId userId, String name, Integer page, Integer limit, String sort, String order) {
        return List.of();
    }

    /**
     *
     *    --------- Block of utility methods -----------------
     *
     */

    public LitemallAddress convertToDataModel(LitemallAddressAggregate addressAggregate) {
        LitemallAddress dataModel = new LitemallAddress();

        if(addressAggregate.getAddressId() != null){
            dataModel.setId(addressAggregate.getAddressId().getId());
        }
        dataModel.setUserId(addressAggregate.getUserId().getId());
        dataModel.setProvince(addressAggregate.getProvince());
        dataModel.setCity(addressAggregate.getCity());
        dataModel.setCounty(addressAggregate.getCounty());
        dataModel.setAddressDetail(addressAggregate.getAddressDetail());

        dataModel.setAreaCode(addressAggregate.getAreaCode());
        dataModel.setPostalCode(addressAggregate.getPostalCode());
        dataModel.setTel(addressAggregate.getTel());
        dataModel.setIsDefault(addressAggregate.getIsDefault());
        dataModel.setAddTime(addressAggregate.getAddTime());
        dataModel.setUpdateTime(addressAggregate.getUpdateTime());
        dataModel.setDeleted(addressAggregate.getDeleted());

        return dataModel;
    }


    public LitemallAddressAggregate convertToDomainModel(LitemallAddress record) {

        if(record == null){
            return null;
        }
        LitemallAddressAggregate domainModel = new LitemallAddressAggregate();

        // Relationship mappings
        domainModel.setAddressId(new LitemallAddressId(record.getId()));
        domainModel.setUserId(new LitemallUserId(record.getId()));
        domainModel.setProvince(record.getProvince());
        domainModel.setCity(record.getCity());
        domainModel.setCounty(record.getCounty());
        domainModel.setAddressDetail(record.getAddressDetail());

        domainModel.setAreaCode(record.getAreaCode());
        domainModel.setPostalCode(record.getPostalCode());
        domainModel.setTel(record.getTel());
        domainModel.setIsDefault(record.getIsDefault());
        domainModel.setAddTime(record.getAddTime());
        domainModel.setUpdateTime(record.getUpdateTime());
        domainModel.setDeleted(record.getDeleted());


        return  domainModel;
    }
}
