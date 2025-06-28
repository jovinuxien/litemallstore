package org.linlinjava.litemall.order.infrastructure.repositories.impl;

import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.db.dao.LitemallOrderMapper;
import org.linlinjava.litemall.db.dao.OrderMapper;
import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.db.domain.LitemallOrderExample;
import org.linlinjava.litemall.db.util.OrderUtil;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregateRoot;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;


@Repository
public class LitemallOrderRepositoryImpl implements LitemallOrderRepository {

    private final LitemallOrderMapper litemallOrderMapper;
    private final OrderMapper orderMapper;

    public LitemallOrderRepositoryImpl(LitemallOrderMapper litemallOrderMapper, OrderMapper orderMapper) {
        this.litemallOrderMapper = litemallOrderMapper;
        this.orderMapper = orderMapper;
    }

    @Override
    public LitemallOrderAggregate findById(LitemallOrderId orderId) {
        return convertToDomainModel(litemallOrderMapper.selectByPrimaryKey(orderId.getId()));
    }



    private String getRandomNum(Integer num) {
        String base = "0123456789";
        Random random = new Random();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < num; i++) {
            int number = random.nextInt(base.length());
            sb.append(base.charAt(number));
        }
        return sb.toString();
    }

    @Override
    public void addOrder(LitemallOrderAggregate order) {
       LitemallOrder litemallOrder = convertToDataModel(order);
       litemallOrder.setAddTime(LocalDateTime.now());
       litemallOrder.setUpdateTime(LocalDateTime.now());

       litemallOrderMapper.insertSelective(litemallOrder);
    }

    @Override
    public int count(LitemallUserId userId) {
        LitemallOrderExample example = new LitemallOrderExample();
        example.or().andUserIdEqualTo(userId.getId()).andDeletedEqualTo(false);
        return (int) litemallOrderMapper.countByExample(example);
    }

    @Override
    public LitemallOrderAggregate findByIdAndUserId(LitemallUserId userId, LitemallOrderId orderId) {
        LitemallOrderExample example = new LitemallOrderExample();
        example.or().andIdEqualTo(orderId.getId()).andUserIdEqualTo(userId.getId()).andDeletedEqualTo(false);
        return convertToDomainModel(litemallOrderMapper.selectOneByExample(example));

    }

    @Override
    public int countByOrderSn(LitemallUserId userId, String orderSn) {
        LitemallOrderExample example = new LitemallOrderExample();
        example.or().andUserIdEqualTo(userId.getId()).andOrderSnEqualTo(orderSn).andDeletedEqualTo(false);
        return (int) litemallOrderMapper.countByExample(example);
    }

    @Override
    public List<LitemallOrderAggregate> queryByOrderStatus(LitemallUserId userId, List<Short> orderStatus, int page, int limit, String sort, String order) {
        LitemallOrderExample example = new LitemallOrderExample();
        example.setOrderByClause(LitemallOrder.Column.addTime.desc());
        LitemallOrderExample.Criteria criteria = example.or();
        criteria.andUserIdEqualTo(userId.getId());
        if (orderStatus != null) {
            criteria.andOrderStatusIn(orderStatus);
        }
        criteria.andDeletedEqualTo(false);
        if (!StringUtils.isEmpty(sort) && !StringUtils.isEmpty(order)) {
            example.setOrderByClause(sort + " " + order);
        }

        PageHelper.startPage(page, limit);
        return litemallOrderMapper.selectByExample(example).stream()
                .map(this::convertToDomainModel).collect(Collectors.toList());
    }

    @Override
    public void deleteByOrderId(LitemallOrderId orderId) {
        litemallOrderMapper.logicalDeleteByPrimaryKey(orderId.getId());
    }

    // TODO This should generate a unique order, but in fact there is still the possibility that two orders are the same.
    @Override
    public String generateOrderSn(LitemallUserId userId) {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyyMMdd");
        String now = df.format(LocalDate.now());
        String orderSn = now + getRandomNum(6);
        while (countByOrderSn(userId, orderSn) != 0) {
            orderSn = now + getRandomNum(6);
        }
        return orderSn;
    }

    @Override
    public int count() {
        LitemallOrderExample example = new LitemallOrderExample();
        example.or().andDeletedEqualTo(false);
        return (int) litemallOrderMapper.countByExample(example);
    }

    @Override
    public List<LitemallOrderAggregate> queryUnPaid(int minutes) {
        LitemallOrderExample example = new LitemallOrderExample();
        example.or().andOrderStatusEqualTo(LitemallOrderStatus.CREATED.getCode()).andDeletedEqualTo(false);
        return litemallOrderMapper.selectByExample(example)
                .stream().map(this::convertToDomainModel).collect(Collectors.toList());
    }

    @Override
    public List<LitemallOrderAggregate> queryUnconfirm(int days) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expired = now.minusDays(days);
        LitemallOrderExample example = new LitemallOrderExample();
        example.or().andOrderStatusEqualTo(LitemallOrderStatus.SHIPPED.getCode()).andShipTimeLessThan(expired).andDeletedEqualTo(false);

        return litemallOrderMapper.selectByExample(example)
                .stream().map(this::convertToDomainModel).collect(Collectors.toList());
    }

    @Override
    public Map<Object, Object> orderInfo(LitemallUserId userId) {
        LitemallOrderExample example = new LitemallOrderExample();
        example.or().andUserIdEqualTo(userId.getId()).andDeletedEqualTo(false);
        List<LitemallOrder> orders = litemallOrderMapper.selectByExampleSelective(example, LitemallOrder.Column.orderStatus, LitemallOrder.Column.comments);

        int unpaid = 0;
        int unship = 0;
        int unrecv = 0;
        int uncomment = 0;
        for (LitemallOrder order : orders) {
            if (OrderUtil.isCreateStatus(order)) {
                unpaid++;
            } else if (OrderUtil.isPayStatus(order)) {
                unship++;
            } else if (OrderUtil.isShipStatus(order)) {
                unrecv++;
            } else if (OrderUtil.isConfirmStatus(order) || OrderUtil.isAutoConfirmStatus(order)) {
                uncomment += order.getComments();
            } else {
                // do nothing
            }
        }

        Map<Object, Object> orderInfo = new HashMap<Object, Object>();
        orderInfo.put("unpaid", unpaid);
        orderInfo.put("unship", unship);
        orderInfo.put("unrecv", unrecv);
        orderInfo.put("uncomment", uncomment);
        return orderInfo;
    }

    @Override
    public List<LitemallOrderAggregate> queryComment(int days) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expired = now.minusDays(days);

        LitemallOrderExample example = new LitemallOrderExample();
        example.or().andCommentsGreaterThan((short) 0).andConfirmTimeLessThan(expired).andDeletedEqualTo(false);

        return litemallOrderMapper.selectByExample(example)
                .stream().map(this::convertToDomainModel).collect(Collectors.toList());
    }

    @Override
    public int updateSelective(LitemallOrderAggregate orderAggregate) {
        LitemallOrder order = convertToDataModel(orderAggregate);
        return litemallOrderMapper.updateByPrimaryKeySelective(order);
    }

    @Override
    public void updateAfterSaleStatus(LitemallOrderId orderId, Short statusReject) {
        LitemallOrder order = new LitemallOrder();
        order.setId(orderId.getId());
        order.setAftersaleStatus(statusReject);
        order.setUpdateTime(LocalDateTime.now());

        litemallOrderMapper.updateByPrimaryKeySelective(order);
    }



    /**
     *
     *    Methods util ------------- utils methods ----------------------
     *
     */

    private  List<LitemallOrderAggregate> queryList(Integer userId, String orderSn, LocalDateTime start, LocalDateTime end, List<Short> orderStatusArray, Integer page, Integer limit, String sort, String order) {
        LitemallOrderExample example = new LitemallOrderExample();
        LitemallOrderExample.Criteria criteria = example.createCriteria();


        if (userId != null) {
            criteria.andUserIdEqualTo(userId);
        }
        if (!org.springframework.util.StringUtils.isEmpty(orderSn)) {
            criteria.andOrderSnEqualTo(orderSn);
        }
        if(start != null){
            criteria.andAddTimeGreaterThanOrEqualTo(start);
        }
        if(end != null){
            criteria.andAddTimeLessThanOrEqualTo(end);
        }
        if (orderStatusArray != null && orderStatusArray.size() != 0) {
            criteria.andOrderStatusIn(orderStatusArray);
        }
        criteria.andDeletedEqualTo(false);

        if (!org.springframework.util.StringUtils.isEmpty(sort) && !StringUtils.isEmpty(order)) {
            example.setOrderByClause(sort + " " + order);
        }

        PageHelper.startPage(page, limit);
        return litemallOrderMapper.selectByExample(example).stream().map(this::convertToDomainModel).collect(Collectors.toList());


    }

    public LitemallOrder convertToDataModel(LitemallOrderAggregate orderAggregate) {

        LitemallOrder dataModel = new LitemallOrder();

        if(orderAggregate.getOrderId().getId() != null){
            dataModel.setId(orderAggregate.getOrderId().getId());
        }
        dataModel.setUserId(orderAggregate.getUserId().getId());
        dataModel.setOrderSn(orderAggregate.getOrderSn());

        dataModel.setOrderStatus(orderAggregate.getOrderStatus().getCode());
        dataModel.setAftersaleStatus(orderAggregate.getAfterSaleStatus().getCode());
        dataModel.setConsignee(orderAggregate.getConsignee());
        dataModel.setMobile(orderAggregate.getMobile());
        dataModel.setAddress(orderAggregate.getAddress());
        dataModel.setMessage(orderAggregate.getMessage());

        dataModel.setGoodsPrice(orderAggregate.getGoodsPrice().getAmount());
        dataModel.setFreightPrice(orderAggregate.getFreightPrice().getAmount());
        dataModel.setCouponPrice(orderAggregate.getCouponPrice().getAmount());
        dataModel.setIntegralPrice(orderAggregate.getIntegralPrice().getAmount());
        dataModel.setGrouponPrice(orderAggregate.getGrouponPrice().getAmount());
        dataModel.setOrderPrice(orderAggregate.getOrderPrice().getAmount());
        dataModel.setActualPrice(orderAggregate.getActualPrice().getAmount());

        dataModel.setPayId(orderAggregate.getPayId());
        dataModel.setPayTime(orderAggregate.getPayTime());
        dataModel.setShipSn(orderAggregate.getShipSn());
        dataModel.setShipChannel(orderAggregate.getShipChannel());
        dataModel.setShipTime(orderAggregate.getShipTime());
        dataModel.setRefundAmount(orderAggregate.getRefundAmount().getAmount());
        dataModel.setRefundType(orderAggregate.getRefundType().toString());

        dataModel.setRefundContent(orderAggregate.getRefundContent());
        dataModel.setRefundTime(orderAggregate.getRefundTime());
        dataModel.setConfirmTime(orderAggregate.getConfirmTime());
        dataModel.setComments(orderAggregate.getComments());

        dataModel.setEndTime(orderAggregate.getEndTime());
        dataModel.setAddTime(orderAggregate.getAddTime());
        dataModel.setUpdateTime(orderAggregate.getUpdateTime());
        dataModel.setDeleted(orderAggregate.getDeleted());



        //Other fields should be completed

        return dataModel;
    }


    public LitemallOrderAggregate convertToDomainModel(LitemallOrder record) {

        LitemallOrderAggregate domainModel = new LitemallOrderAggregate();
        LitemallAddressAggregate addressAggregate = new LitemallAddressAggregate();
        if(record == null){
            return null;
        }
        // Relationship mappings
        domainModel.setOrderId(new LitemallOrderId(record.getId()));
        domainModel.setUserId(new LitemallUserId(record.getUserId()));
        domainModel.setOrderSn(record.getOrderSn());
        domainModel.setOrderStatus(LitemallOrderStatus.valueOf(record.getOrderStatus().toString()));
        domainModel.setAfterSaleStatus(LitemallAfterSaleStatus.valueOf(record.getAftersaleStatus().toString()));


        domainModel.setConsignee(record.getConsignee());
        domainModel.setMobile(record.getMobile());
        domainModel.setAddress(addressAggregate.getName());
        domainModel.setMessage(record.getMessage());

        domainModel.setGoodsPrice(new LitemallMoney(record.getGoodsPrice()));
        domainModel.setFreightPrice(new LitemallMoney(record.getFreightPrice()));
        domainModel.setCouponPrice(new LitemallMoney(record.getCouponPrice()));
        domainModel.setIntegralPrice(new LitemallMoney(record.getIntegralPrice()));
        domainModel.setGrouponPrice(new LitemallMoney(record.getGrouponPrice()));
        domainModel.setOrderPrice(new LitemallMoney(record.getOrderPrice()));
        domainModel.setActualPrice(new LitemallMoney(record.getActualPrice()));
        domainModel.setPayId(record.getPayId());
        domainModel.setPayTime(record.getPayTime());
        domainModel.setShipSn(record.getShipSn());
        domainModel.setShipChannel(record.getShipChannel());
        domainModel.setShipTime(record.getShipTime());


        domainModel.setRefundAmount(new LitemallMoney(record.getRefundAmount()));
        domainModel.setRefundType(record.getRefundType());
        domainModel.setRefundContent(record.getRefundContent());
        domainModel.setRefundTime(record.getRefundTime());
        domainModel.setConfirmTime(record.getConfirmTime());
        domainModel.setComments(record.getComments());

        domainModel.setEndTime(record.getEndTime());
        domainModel.setAddTime(record.getAddTime());
        domainModel.setUpdateTime(record.getUpdateTime());
        domainModel.setDeleted(record.getDeleted());

        // Orther fields
        return  domainModel;
    }
}
