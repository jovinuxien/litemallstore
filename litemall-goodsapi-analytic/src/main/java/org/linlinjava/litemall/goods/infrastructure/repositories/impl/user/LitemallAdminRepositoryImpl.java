package org.linlinjava.litemall.goods.infrastructure.repositories.impl.user;

import org.linlinjava.litemall.goods.domain.model.agregates.user.LitemallAdminAggregate;
import org.linlinjava.litemall.goods.domain.model.repositories.user.LitemallAdminRepository;
import org.linlinjava.litemall.goods.domain.model.valueobjects.user.LitemallAdminId;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class LitemallAdminRepositoryImpl implements LitemallAdminRepository {

    private final LitemallAdmin.Column[] result = new LitemallAdmin.Column[]{LitemallAdmin.Column.id, LitemallAdmin.Column.username, LitemallAdmin.Column.avatar, LitemallAdmin.Column.roleIds};
    @Resource
    private LitemallAdminMapper adminMapper;

    @Override
    public LitemallAdminAggregate findById(LitemallAdminId adminId) {
        return convertToDomainModel(adminMapper.selectByPrimaryKeySelective(adminId.getId(), result));
    }

    @Override
    public LitemallAdminAggregate findByUsername(String username) {
        LitemallAdminExample example = new LitemallAdminExample();
        example.or().andUsernameEqualTo(username).andDeletedEqualTo(false);
        return convertToDomainModel(adminMapper.selectOneByExample(example));
    }

    @Override
    public void saveAdmin(LitemallAdminAggregate admin) {
        LitemallAdmin adminDataModel = convertToDataModel(admin);
        admin.setAddTime(LocalDateTime.now());
        admin.setUpdateTime(LocalDateTime.now());
        adminMapper.insertSelective(adminDataModel);
    }

    @Override
    public int updateAdmin(LitemallAdminAggregate admin) {
      return adminMapper.updateByPrimaryKey(convertToDataModel(admin));
    }


    @Override
    public void deleteById(LitemallAdminId adminId) {
        adminMapper.logicalDeleteByPrimaryKey(adminId.getId());
    }

    @Override
    public int countUser() {
        return 0;
    }

    @Override
    public List<LitemallAdminAggregate> all() {
        LitemallAdminExample example = new LitemallAdminExample();
        example.or().andDeletedEqualTo(false);
        return adminMapper.selectByExample(example).stream().map(this::convertToDomainModel).toList();
    }

    public LitemallAdminAggregate convertToDomainModel(LitemallAdmin record) {
        if(record == null){
            return null;
        }
        LitemallAdminAggregate adminAggregate = new LitemallAdminAggregate();

        // Relationship mappings
        adminAggregate.setAdminId(new LitemallAdminId(record.getId()));
        adminAggregate.setUsername(record.getUsername());
        adminAggregate.setPassword(record.getPassword());



        adminAggregate.setAddTime(record.getAddTime());
        adminAggregate.setUpdateTime(record.getUpdateTime());
        adminAggregate.setDeleted(record.getDeleted());

        return adminAggregate;
    }

    public LitemallAdmin convertToDataModel(LitemallAdminAggregate adminAggregate) {

        LitemallAdmin dataModel = new LitemallAdmin();

        if(adminAggregate.getAdminId() != null){
            dataModel.setId(adminAggregate.getAdminId().getId());
        }
        dataModel.setUsername(adminAggregate.getUsername());
        dataModel.setPassword(adminAggregate.getPassword());



        dataModel.setAddTime(adminAggregate.getAddTime());
        dataModel.setUpdateTime(adminAggregate.getUpdateTime());
        dataModel.setDeleted(adminAggregate.isDeleted());

        return dataModel;
    }
}
