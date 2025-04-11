package org.linlinjava.litemall.order.infrastructure.repositories.impl;


import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.db.dao.LitemallCouponMapper;
import org.linlinjava.litemall.db.dao.LitemallCouponUserMapper;
import org.linlinjava.litemall.db.dao.LitemallOrderMapper;
import org.linlinjava.litemall.db.domain.LitemallCoupon;
import org.linlinjava.litemall.db.domain.LitemallCouponUser;
import org.linlinjava.litemall.db.domain.LitemallCouponUserExample;
import org.linlinjava.litemall.db.util.CouponUserConstant;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCouponAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCouponUserAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCouponUserRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCouponUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallCouponUserStatus;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Repository
public class LitemallCouponUserRepositoryImpl implements LitemallCouponUserRepository {

    private final LitemallCouponUserMapper couponUserMapper;
    private final LitemallOrderMapper orderMapper;
    private final LitemallCouponMapper couponMapper;

    public LitemallCouponUserRepositoryImpl(LitemallCouponUserMapper couponUserMapper, LitemallOrderMapper orderMapper, LitemallCouponMapper couponMapper) {
        this.couponUserMapper = couponUserMapper;
        this.orderMapper = orderMapper;
        this.couponMapper = couponMapper;
    }

    @Override
    public int countCoupon(LitemallCouponId couponId) {
        return 0;
    }

    @Override
    public void add(LitemallCouponId couponId, LitemallUserId userId) {

    }

    @Override
    public int countUserAndCoupon(LitemallCouponId couponId, LitemallUserId userId) {
        return 0;
    }

    @Override
    public List<LitemallCouponUserAggregate> queryListCouponUser(LitemallUserId userId, LitemallCouponId couponId, Short status, Integer page, Integer size, String sort, String order) {
        return List.of();
    }

    @Override
    public List<LitemallCouponUserAggregate> findAll(LitemallCouponId couponId, LitemallUserId userId) {
        List<LitemallCouponUserAggregate> result = queryList(userId.getId(), couponId.getId(), LitemallCouponUserStatus.USABLE.getValue(), null, null, "add_time", "des");

        if(result.isEmpty()) {
            return List.of();
        }
        return result;
    }

    @Override
    public List<LitemallCouponUserAggregate> findAllByUser(LitemallUserId userId) {
        List<LitemallCouponUserAggregate> result = queryList(userId.getId(), null, LitemallCouponUserStatus.USABLE.getValue(), null, null, "add_time", "des");

        if(result.isEmpty()) {
            return List.of();
        }
        return result;
    }

    @Override
    public List<LitemallCouponUserAggregate> queryExpiredCoupon() {
        LitemallCouponUserExample example = new LitemallCouponUserExample();
        example.or().andStatusEqualTo(CouponUserConstant.STATUS_USABLE).andEndTimeLessThan(LocalDateTime.now()).andDeletedEqualTo(false);
        return couponUserMapper.selectByExample(example).stream().map(this::convertToDomainModel).toList();
    }


    @Override
    public LitemallCouponUserAggregate findOne(LitemallCouponId couponId, LitemallUserId userId) {
        List<LitemallCouponUserAggregate> result = queryList(userId.getId(), couponId.getId(), LitemallCouponUserStatus.USABLE.getValue(), 1, 1, "add_time", "des");
        if (result.isEmpty()) {
            return null;
        }
        return result.get(0);
    }

    @Override
    public int updateCouponUser(LitemallCouponUserAggregate couponUserAggregate) {
        return couponUserMapper.updateByPrimaryKey(convertToDataModel(couponUserAggregate));
    }



    @Override
    public LitemallCouponUserAggregate findByOrderId(LitemallOrderId orderId) {
        return null;
    }

    @Override
    public LitemallCouponUserAggregate findById(LitemallCouponUserId couponUserId) {
        return null;
    }


    private List<LitemallCouponUserAggregate> queryList(Integer userId, Integer couponId, Short status, Integer page, Integer size, String sort, String order) {

        LitemallCouponUserExample example = new LitemallCouponUserExample();
        LitemallCouponUserExample.Criteria criteria = example.createCriteria();


        if (userId != null) {
            criteria.andUserIdEqualTo(userId);
        }
        if(couponId != null){
            criteria.andCouponIdEqualTo(couponId);
        }
        if (status != null) {
            criteria.andStatusEqualTo(status);
        }
        criteria.andDeletedEqualTo(false);

        if (!StringUtils.isEmpty(sort) && !StringUtils.isEmpty(order)) {
            example.setOrderByClause(sort + " " + order);
        }

        if (!StringUtils.isEmpty(page) && !StringUtils.isEmpty(size)) {
            PageHelper.startPage(page, size);
        }

        return couponUserMapper.selectByExample(example).stream().map(this::convertToDomainModel).collect(Collectors.toList());
    }


    /**
     *
     *    --------- Block of utility methods -----------------
     *
     */

    public LitemallCouponUser convertToDataModel(LitemallCouponUserAggregate couponUserAggregate) {
        LitemallCouponUser dataModel = new LitemallCouponUser();

        if(couponUserAggregate.getCouponUserId() != null){
            dataModel.setId(couponUserAggregate.getCouponUserId().getId());
        }
        dataModel.setCouponId(couponUserAggregate.getCouponId().getId());
        dataModel.setUserId(couponUserAggregate.getUserId().getId());
        dataModel.setOrderId(couponUserAggregate.getOrderId().getId());
        dataModel.setStatus(couponUserAggregate.getStatus().getValue());
        dataModel.setUsedTime(couponUserAggregate.getUsedTime());
        dataModel.setAddTime(couponUserAggregate.getAddTime());
        return dataModel;
    }


    public LitemallCouponUserAggregate convertToDomainModel(LitemallCouponUser record) {

        if(record == null){
            return null;
        }

        LitemallCouponUserAggregate domainModel = new LitemallCouponUserAggregate();

        // Relationship mappings
        domainModel.setCouponUserId(new LitemallCouponUserId(record.getId()));
        domainModel.setCouponId(new LitemallCouponId(record.getId()));
        domainModel.setUserId(new LitemallUserId(record.getId()));
        domainModel.setOrderId(new LitemallOrderId(record.getId()));

        // Orther fields
        domainModel.setUsedTime(record.getUsedTime());
        domainModel.setAddTime(record.getAddTime());


        domainModel.setStatus(LitemallCouponUserStatus.valueOf(record.getStatus().toString()));

        return  domainModel;
    }
}
