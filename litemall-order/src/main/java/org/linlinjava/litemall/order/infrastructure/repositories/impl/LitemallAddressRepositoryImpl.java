package org.linlinjava.litemall.order.infrastructure.repositories.impl;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallAddressRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class LitemallAddressRepositoryImpl implements LitemallAddressRepository {

    private final LitemallAddressMapper addressMapper;
    
    public LitemallAddressRepositoryImpl(LitemallAddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    @Override
    public List<LitemallAddressAggregate> getListAddressesByUserId(LitemallUserId userId) {
        LitemallAddressExample example = new LitemallAddressExample();
        example.or().andUserIdEqualTo(userId.getId()).andDeletedEqualTo(false);
        example.setOrderByClause("is_default DESC, add_time DESC");
        return addressMapper.selectByExample(example).stream()
                .map(this::convertToDomainModel)
                .collect(Collectors.toList());
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
        if (litemallAddress.getDeleted() == null) {
            litemallAddress.setDeleted(false);
        }
        if (litemallAddress.getIsDefault() == null) {
            litemallAddress.setIsDefault(false);
        }
        int rows = addressMapper.insertSelective(litemallAddress);
        // MyBatis populates the generated key on the data model; surface it on the
        // aggregate so callers (the /save endpoint) can return the persisted id.
        if (litemallAddress.getId() != null) {
            address.setAddressId(new LitemallAddressId(litemallAddress.getId()));
        }
        return rows;
    }

    @Override
    public int updateAddress(LitemallAddressAggregate addressAggregate) {
        LitemallAddress address = convertToDataModel(addressAggregate);
        address.setUpdateTime(LocalDateTime.now());
        return addressMapper.updateByPrimaryKeySelective(address);
    }

    @Override
    public int deleteAddress(LitemallAddressId addressId) {
        return addressMapper.logicalDeleteByPrimaryKey(addressId.getId());
    }

    @Override
    public void resetDefaultAddress(LitemallUserId userId) {
        // Clear the default flag on all of the user's live addresses before a new
        // default is set, so at most one address is ever the default.
        LitemallAddressExample example = new LitemallAddressExample();
        example.or().andUserIdEqualTo(userId.getId())
                .andIsDefaultEqualTo(true).andDeletedEqualTo(false);
        LitemallAddress patch = new LitemallAddress();
        patch.setIsDefault(false);
        patch.setUpdateTime(LocalDateTime.now());
        addressMapper.updateByExampleSelective(patch, example);
    }

    @Override
    public List<LitemallAddressAggregate> findAddresses(LitemallUserId userId, String name, Integer page, Integer limit, String sort, String order) {
        LitemallAddressExample example = new LitemallAddressExample();
        LitemallAddressExample.Criteria criteria = example.or()
                .andUserIdEqualTo(userId.getId()).andDeletedEqualTo(false);
        if (name != null && !name.isBlank()) {
            criteria.andNameLike("%" + name + "%");
        }
        String sortColumn = (sort == null || sort.isBlank()) ? "add_time" : sort;
        String sortOrder = (order == null || order.isBlank()) ? "desc" : order;
        example.setOrderByClause(sortColumn + " " + sortOrder);
        return addressMapper.selectByExample(example).stream()
                .map(this::convertToDomainModel)
                .collect(Collectors.toList());
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
        dataModel.setName(addressAggregate.getName());
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
        domainModel.setUserId(new LitemallUserId(record.getUserId()));
        domainModel.setName(record.getName());
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
