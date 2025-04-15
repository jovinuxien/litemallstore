package org.linlinjava.litemall.order.infrastructure.repositories.impl;

import com.alibaba.druid.util.StringUtils;
import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.db.dao.LitemallCouponMapper;
import org.linlinjava.litemall.db.dao.LitemallCouponUserMapper;
import org.linlinjava.litemall.db.domain.LitemallCoupon.Column;
import org.linlinjava.litemall.db.domain.LitemallCoupon;
import org.linlinjava.litemall.db.domain.LitemallCouponExample;
import org.linlinjava.litemall.db.domain.LitemallCouponUser;
import org.linlinjava.litemall.db.domain.LitemallCouponUserExample;
import org.linlinjava.litemall.db.util.CouponConstant;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCouponAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCouponRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallUserId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;


@Repository
public class LitemallCouponRepositoryImpl implements LitemallCouponRepository {

    private  final LitemallCouponMapper couponMapper;
    private  final LitemallCouponUserMapper couponUserMapper;
    private final  Column[] result = new Column[]{Column.id, Column.name, Column.desc, Column.tag,
                                            Column.days, Column.startTime, Column.endTime,
                                            Column.discount, Column.min};

    @Autowired
    public LitemallCouponRepositoryImpl(LitemallCouponMapper litemallCouponMapper, LitemallCouponUserMapper litemallCouponUserMapper) {
        this.couponMapper = litemallCouponMapper;
        this.couponUserMapper = litemallCouponUserMapper;
    }



    @Override
    public void save(LitemallCouponAggregate couponAggregate) {
       if(couponAggregate.getCouponId() == null){
           LitemallCoupon coupon = convertToDataModel(couponAggregate);
           couponMapper.insert(coupon);
       }
    }

    @Override
    public int update(LitemallCouponAggregate couponAggregate) {
        LitemallCoupon coupon = convertToDataModel(couponAggregate);
        coupon.setUpdateTime(couponAggregate.getUpdateTime());

        return couponMapper.updateByPrimaryKeySelective(coupon);
    }

    @Override
    public void remove(LitemallCouponId id) {
      couponMapper.logicalDeleteByPrimaryKey(id.getId());
    }

    @Override
    public Optional<LitemallCouponAggregate> findById(LitemallCouponId id) {
        if(id == null){
            return Optional.empty();
        }
        LitemallCoupon coupon = couponMapper.selectByPrimaryKey(id.getId());
        return Optional.of(convertToDomainModel(coupon));
    }

    @Override
    public List<LitemallCouponAggregate> findAvailableCouponForUser(LitemallUserId userId, int offset, int limit) {

        // First get the used coupons first: Joining like query
        List<LitemallCouponUser> used = couponUserMapper.selectByExample(
                LitemallCouponUserExample.newAndCreateCriteria()
                        .andUserIdEqualTo(userId.getId())
                        .example()
        );

        //Query based on criteria
        LitemallCouponExample.Criteria criteria = LitemallCouponExample.newAndCreateCriteria()
                .andTypeEqualTo(CouponConstant.TYPE_COMMON)
                .andStatusEqualTo(CouponConstant.STATUS_NORMAL)
                .andDeletedEqualTo(false);

        if(used != null && !used.isEmpty()){
            criteria.andIdNotIn(used.stream().map(
             LitemallCouponUser::getCouponId)
                    .collect(Collectors.toList()));
        }

        PageHelper.startPage(offset, limit);
        List<LitemallCoupon> records =
                couponMapper.selectByExampleSelective(criteria.example(), result);

        return records.stream()
                .map(this::convertToDomainModel)
                .collect(Collectors.toList());
    }

    @Override
    public List<LitemallCouponAggregate> queryCouponSelective(String name, Short type, Short status, Integer page, Integer limit, String sort, String order) {
        LitemallCouponExample example = new LitemallCouponExample();
        LitemallCouponExample.Criteria criteria = example.createCriteria();

        if (!StringUtils.isEmpty(name)) {
            criteria.andNameLike("%" + name + "%");
        }
        if (type != null) {
            criteria.andTypeEqualTo(type);
        }
        if (status != null) {
            criteria.andStatusEqualTo(status);
        }
        criteria.andDeletedEqualTo(false);

        if (!StringUtils.isEmpty(sort) && !StringUtils.isEmpty(order)) {
            example.setOrderByClause(sort + " " + order);
        }

        PageHelper.startPage(page, limit);

        return couponMapper.selectByExample(example)
                .stream()
                .map(this::convertToDomainModel)
                .collect(Collectors.toList());
    }

    @Override
    public LitemallCouponAggregate findByCode(String code) {
       LitemallCouponExample example = new LitemallCouponExample();
       example.or().andCodeEqualTo(code).andTimeTypeEqualTo(CouponConstant.TYPE_CODE).andStatusEqualTo(CouponConstant.STATUS_NORMAL).andDeletedEqualTo(false);
       List<LitemallCoupon> couponList = couponMapper.selectByExample(example);

        if(couponList.size() > 1){
            throw new RuntimeException("");
        }
        else if(couponList.size() == 0){
            return null;
        }
        else {
            return couponList.stream().map(this::convertToDomainModel).toList().get(0);
        }
    }

    @Override
    public List<LitemallCouponAggregate> findExpiredCoupons() {
        LitemallCouponExample example = new LitemallCouponExample();
        example.createCriteria()
                .andStatusEqualTo(CouponConstant.STATUS_NORMAL)
                .andTimeTypeEqualTo(CouponConstant.TIME_TYPE_TIME)
                .andEndTimeLessThan(LocalDateTime.now())
                .andDeletedEqualTo(false);

        return couponMapper.selectByExample(example)
                .stream()
                .map(this::convertToDomainModel)
                .collect(Collectors.toList());
    }

    @Override
    public int countCoupon(LitemallCouponId couponId) {
        LitemallCouponExample example = new LitemallCouponExample();

        example.or().andIdEqualTo(couponId.getId()).andDeletedEqualTo(false);
        return (int) couponMapper.countByExample(example);
    }






    /**
     *
     *    --------- Block of utility methods -----------------
     *
     */







    private String getRandomNum(Integer num) {
        String base = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        base += "0123456789";

        Random random = new Random();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < num; i++) {
            int number = random.nextInt(base.length());
            sb.append(base.charAt(number));
        }
        return sb.toString();
    }



    public String generateCode() {
        String code = getRandomNum(8);
        while(findByCode(code) != null){
            code = getRandomNum(8);
        }
        return code;
    }

    public LitemallCoupon convertToDataModel(LitemallCouponAggregate couponAggregate) {
        LitemallCoupon dataModel = new LitemallCoupon();

            if(couponAggregate.getCouponId() != null){
                dataModel.setId(couponAggregate.getCouponId().getId());
            }
            dataModel.setName(couponAggregate.getName());
            dataModel.setDiscount(couponAggregate.getDiscount());

            return dataModel;
    }


    public LitemallCouponAggregate convertToDomainModel(LitemallCoupon record) {
        if(record == null){
            return null;
        }

        LitemallCouponAggregate domainModel = new LitemallCouponAggregate();

        domainModel.setCouponId(new LitemallCouponId(record.getId()));
        domainModel.setName(record.getName());

        return  domainModel;
    }
}
