package org.linlinjava.litemall.goods.infrastructure.repositories.impl.user;

import org.linlinjava.litemall.db.dao.LitemallPermissionMapper;
import org.linlinjava.litemall.db.domain.LitemallBrand;
import org.linlinjava.litemall.db.domain.LitemallPermission;
import org.linlinjava.litemall.db.domain.LitemallPermissionExample;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallBrandAggregate;
import org.linlinjava.litemall.goods.domain.model.agregates.user.LitemallPermissionAggregate;
import org.linlinjava.litemall.goods.domain.model.repositories.user.LitemallPermissionRepository;
import org.linlinjava.litemall.goods.domain.model.valueobjects.manufacturer.LitemallManufacturerId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.user.LitemallPermissionId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.user.LitemallRoleId;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Repository
public class LitemallPermissionRepositoryImpl implements LitemallPermissionRepository {


    @Resource
    private LitemallPermissionMapper permissionMapper;



    @Override
    public Set<String> queryByRoleIds(Integer[] roleIds) {
        Set<String> permissions = new HashSet<String>();


        if(roleIds.length == 0){
            return permissions;
        }

        LitemallPermissionExample example = new LitemallPermissionExample();
        example.or().andRoleIdIn(Arrays.asList(roleIds)).andDeletedEqualTo(false);
        List<LitemallPermission> permissionList = permissionMapper.selectByExample(example);

        for(LitemallPermission permission : permissionList){
            permissions.add(permission.getPermission());
        }

        return permissions;
    }

    @Override
    public Set<String> queryByRoleId(Integer roleId) {
        Set<String> permissions = new HashSet<String>();

        if(roleId == null){
            return permissions;
        }

        LitemallPermissionExample example = new LitemallPermissionExample();
        example.or().andRoleIdEqualTo(roleId).andDeletedEqualTo(false);
        List<LitemallPermission> permissionList = permissionMapper.selectByExample(example);

        for(LitemallPermission permission : permissionList){
            permissions.add(permission.getPermission());
        }
        return permissions;
    }

    @Override
    public boolean checkSuperPermission(Integer roleId) {
        if(roleId == null){
            return false;
        }

        LitemallPermissionExample example = new LitemallPermissionExample();
        example.or().andRoleIdEqualTo(roleId).andPermissionEqualTo("*").andDeletedEqualTo(false);
        return permissionMapper.countByExample(example) != 0;
    }

    @Override
    public boolean checkSuperPermission(List<Integer> roleIds) {
        if(roleIds == null || roleIds.isEmpty()){
            return false;
        }

        LitemallPermissionExample example = new LitemallPermissionExample();
        example.or().andRoleIdIn(roleIds).andPermissionEqualTo("*").andDeletedEqualTo(false);
        return permissionMapper.countByExample(example) != 0;
    }

    @Override
    public void savePermission(LitemallPermissionAggregate permissionAggregate) {
       var litemallPermission = convertToDataModel(permissionAggregate);
        litemallPermission.setAddTime(LocalDateTime.now());
        litemallPermission.setUpdateTime(LocalDateTime.now());
        permissionMapper.insertSelective(litemallPermission);
    }



    @Override
    public void deleteByRoleId(LitemallRoleId roleId) {
        LitemallPermissionExample example = new LitemallPermissionExample();
        example.or().andRoleIdEqualTo(roleId.getId()).andDeletedEqualTo(false);
        permissionMapper.logicalDeleteByExample(example);
    }

    public LitemallPermissionAggregate convertToDomainModel(LitemallPermission record) {

        if(record == null){
            return null;
        }
        LitemallPermissionAggregate permissionAggregate = new LitemallPermissionAggregate();

        // Relationship mappings
        permissionAggregate.setPermissionId(new LitemallPermissionId(record.getId()));
        permissionAggregate.setRoleId(new LitemallRoleId(record.getRoleId()));
        permissionAggregate.setPermission(record.getPermission());



        permissionAggregate.setAddTime(record.getAddTime());
        permissionAggregate.setUpdateTime(record.getUpdateTime());
        permissionAggregate.setDeleted(record.getDeleted());

        return permissionAggregate;
    }

    public LitemallPermission convertToDataModel(LitemallPermissionAggregate permissionAggregate) {

        LitemallPermission dataModel = new LitemallPermission();

        if(permissionAggregate.getPermissionId() != null){
            dataModel.setId(permissionAggregate.getPermissionId().getId());
        }
        dataModel.setRoleId(permissionAggregate.getRoleId().getId());
        dataModel.setPermission(permissionAggregate.getPermission());


        dataModel.setAddTime(permissionAggregate.getAddTime());
        dataModel.setUpdateTime(permissionAggregate.getUpdateTime());
        dataModel.setDeleted(permissionAggregate.isDeleted());

        return dataModel;
    }
}
