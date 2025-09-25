package org.linlinjava.litemall.goods.infrastructure.repositories.impl.user;

import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.db.dao.LitemallUserMapper;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.domain.LitemallUserExample;
import org.linlinjava.litemall.goods.domain.model.agregates.user.LitemallUserAggregate;
import org.linlinjava.litemall.goods.domain.model.repositories.user.LitemallUserRepository;
import org.linlinjava.litemall.goods.domain.model.valueobjects.user.LitemallUserId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;


@Repository
public class LitemallUserRepositoryImpl implements LitemallUserRepository {


    @Autowired
    private  LitemallUserMapper userMapper;



    @Override
    public LitemallUserAggregate findById(LitemallUserId userId) {
        return convertToDomainModel(userMapper.selectByPrimaryKey(userId.getId()));
    }

    @Override
    public List<LitemallUserAggregate> queryByUsername(String username) {
        LitemallUserExample example = new LitemallUserExample();
        example.or().andUsernameEqualTo(username).andDeletedEqualTo(false);
        return userMapper.selectByExample(example).stream().map(this::convertToDomainModel).toList();
    }

    @Override
    public List<LitemallUserAggregate> querySelective(String username, String mobile, Integer page, Integer size, String sort, String order) {
        LitemallUserExample example = new LitemallUserExample();
        LitemallUserExample.Criteria criteria = example.createCriteria();

        if (!StringUtils.isEmpty(username)) {
            criteria.andUsernameLike("%" + username + "%");
        }
        if (!StringUtils.isEmpty(mobile)) {
            criteria.andMobileEqualTo(mobile);
        }
        criteria.andDeletedEqualTo(false);

        if (!StringUtils.isEmpty(sort) && !StringUtils.isEmpty(order)) {
            example.setOrderByClause(sort + " " + order);
        }

        PageHelper.startPage(page, size);

        return userMapper.selectByExample(example).stream().map(this::convertToDomainModel).toList();
    }

    @Override
    public void saveUser(LitemallUserAggregate userAggregate) {
        var user = convertToDataModel(userAggregate);
        user.setAddTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insertSelective(user);
    }

    @Override
    public void deleteById(LitemallUserId userId) {
        userMapper.logicalDeleteByPrimaryKey(userId.getId());
    }

    @Override
    public int countUser() {
        LitemallUserExample example = new LitemallUserExample();
        example.or().andDeletedEqualTo(false);

        return (int) userMapper.countByExample(example);
    }

    public LitemallUserAggregate convertToDomainModel(LitemallUser record) {
        if(record == null){
            return null;
        }
        LitemallUserAggregate userAggregate = new LitemallUserAggregate();

        // Relationship mappings
        userAggregate.setUserId(new LitemallUserId(record.getId()));
        userAggregate.setUsername(record.getUsername());
        userAggregate.setPassword(record.getPassword());

        userAggregate.setGender(record.getGender());
        userAggregate.setBirthday(record.getBirthday());


        userAggregate.setAddTime(record.getAddTime());
        userAggregate.setUpdateTime(record.getUpdateTime());
        userAggregate.setDeleted(record.getDeleted());

        return userAggregate;
    }

    public LitemallUser convertToDataModel(LitemallUserAggregate userAggregate) {

        LitemallUser dataModel = new LitemallUser();

        if(userAggregate.getUserId() != null){
            dataModel.setId(userAggregate.getUserId().getId());
        }
        dataModel.setUsername(userAggregate.getUsername());
        dataModel.setPassword(userAggregate.getPassword());
        dataModel.setGender(userAggregate.getGender());

        dataModel.setBirthday(userAggregate.getBirthday());


        dataModel.setAddTime(userAggregate.getAddTime());
        dataModel.setUpdateTime(userAggregate.getUpdateTime());
        dataModel.setDeleted(userAggregate.isDeleted());

        return dataModel;
    }
}
