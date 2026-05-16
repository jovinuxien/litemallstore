package org.linlinjava.litemall.goods.infrastructure.repositories.impl.user;

import org.linlinjava.litemall.goods.domain.model.agregates.user.LitemallRoleAggregate;
import org.linlinjava.litemall.goods.domain.model.repositories.user.LitemallRoleRepository;
import org.linlinjava.litemall.goods.domain.model.valueobjects.user.LitemallRoleId;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;


@Repository
public class LitemallRoleRepositoryImpl implements LitemallRoleRepository {


    @Resource
    private LitemallRoleMapper roleMapper;


    @Override
    public LitemallRoleAggregate findById(LitemallRoleId roleId) {
        return convertToDomainModel(roleMapper.selectByPrimaryKey(roleId.getId()));
    }

    @Override
    public void saveRole(LitemallRoleAggregate roleAggregate) {
        LitemallRole roleDataModel = convertToDataModel(roleAggregate);
        roleDataModel.setAddTime(LocalDateTime.now());
        roleDataModel.setUpdateTime(LocalDateTime.now());
        roleMapper.insertSelective(roleDataModel);
    }

    @Override
    public void deleteById(LitemallRoleId roleId) {
        roleMapper.logicalDeleteByPrimaryKey(roleId.getId());
    }


    public LitemallRoleAggregate convertToDomainModel(LitemallRole record) {
        if(record == null){
            return null;
        }
        LitemallRoleAggregate roleAggregate = new LitemallRoleAggregate();

        // Relationship mappings
        roleAggregate.setRoleId(new LitemallRoleId(record.getId()));
        roleAggregate.setName(record.getName());
        roleAggregate.setDesc(record.getDesc());


        roleAggregate.setAddTime(record.getAddTime());
        roleAggregate.setUpdateTime(record.getUpdateTime());
        roleAggregate.setDeleted(record.getDeleted());

        return roleAggregate;
    }

    public LitemallRole convertToDataModel(LitemallRoleAggregate roleAggregate) {

        LitemallRole dataModel = new LitemallRole();

        if(roleAggregate.getRoleId() != null){
            dataModel.setId(roleAggregate.getRoleId().getId());
        }
        dataModel.setName(roleAggregate.getName());
        dataModel.setDesc(roleAggregate.getDesc());



        dataModel.setAddTime(roleAggregate.getAddTime());
        dataModel.setUpdateTime(roleAggregate.getUpdateTime());
        dataModel.setDeleted(roleAggregate.isDeleted());

        return dataModel;
    }
}
