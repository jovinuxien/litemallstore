package org.linlinjava.litemall.order.infrastructure.repositories.impl;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.db.dao.OrderMapper;
import org.linlinjava.litemall.db.util.OrderUtil;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
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
import java.util.*;
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
    public Optional<LitemallOrderAggregate> findById(LitemallOrderId orderId) {
        LitemallOrder record = litemallOrderMapper.selectByPrimaryKey(orderId.getId());
        return record == null ? Optional.empty() : Optional.of(convertToDomainModel(record));
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
       // Propagate the DB-generated primary key back onto the aggregate; placeOrder
       // re-loads the order and attaches order-goods by this id (was left at 0).
       order.setOrderId(new LitemallOrderId(litemallOrder.getId()));
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
    /**
     * Orders carrying this order_sn, across ALL users and INCLUDING soft-deleted rows —
     * the scope the DB's uk_order_order_sn enforces and that CJ's merchant-order dedupe
     * assumes. See {@link #generateOrderSn}.
     */
    public int countByOrderSn(String orderSn) {
        LitemallOrderExample example = new LitemallOrderExample();
        example.or().andOrderSnEqualTo(orderSn);
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
    public int countByOrderStatus(LitemallUserId userId, List<Short> orderStatus) {
        LitemallOrderExample example = new LitemallOrderExample();
        LitemallOrderExample.Criteria criteria = example.or();
        criteria.andUserIdEqualTo(userId.getId());
        if (orderStatus != null) {
            criteria.andOrderStatusIn(orderStatus);
        }
        criteria.andDeletedEqualTo(false);
        return (int) litemallOrderMapper.countByExample(example);
    }

    private LitemallOrderExample adminExample(String orderSn, List<Short> orderStatus,
                                              LocalDateTime start, LocalDateTime end) {
        LitemallOrderExample example = new LitemallOrderExample();
        LitemallOrderExample.Criteria criteria = example.or();
        if (!StringUtils.isEmpty(orderSn)) {
            criteria.andOrderSnEqualTo(orderSn);
        }
        if (orderStatus != null && !orderStatus.isEmpty()) {
            criteria.andOrderStatusIn(orderStatus);
        }
        // Wave 4: optional placement-time window (add_time >= start, < end) — shared by
        // the admin list and the CSV export so both filter identically.
        if (start != null) {
            criteria.andAddTimeGreaterThanOrEqualTo(start);
        }
        if (end != null) {
            criteria.andAddTimeLessThan(end);
        }
        criteria.andDeletedEqualTo(false);
        return example;
    }

    @Override
    public List<LitemallOrderAggregate> adminQuery(String orderSn, List<Short> orderStatus,
                                                   LocalDateTime start, LocalDateTime end,
                                                   int page, int limit, String sortColumn, String order) {
        LitemallOrderExample example = adminExample(orderSn, orderStatus, start, end);
        // sortColumn is whitelisted by the caller; order normalised here.
        String dir = "asc".equalsIgnoreCase(order) ? "asc" : "desc";
        example.setOrderByClause(sortColumn + " " + dir);
        PageHelper.startPage(page, limit);
        return litemallOrderMapper.selectByExample(example).stream()
                .map(this::convertToDomainModel).collect(Collectors.toList());
    }

    @Override
    public long adminCount(String orderSn, List<Short> orderStatus,
                           LocalDateTime start, LocalDateTime end) {
        return litemallOrderMapper.countByExample(adminExample(orderSn, orderStatus, start, end));
    }

    @Override
    public void deleteByOrderId(LitemallOrderId orderId) {
        litemallOrderMapper.logicalDeleteByPrimaryKey(orderId.getId());
    }

    /**
     * A fresh order_sn: {@code yyyyMMdd} + 6 random digits.
     *
     * <p>The pre-check is GLOBAL and includes soft-deleted rows (Wave 7, Task E3). It used
     * to be scoped per-user and to non-deleted rows, which did not match how the value is
     * consumed: reconciliation treats order_sn as globally unique, and CJ dedupes the
     * merchant orderNumber on it — so two users could legitimately hold the same sn on one
     * day and CJ would silently treat the second order as a duplicate of the first. A
     * soft-deleted row's sn could also be reissued while CJ still remembered it.
     *
     * <p>The loop is now a courtesy, not the guarantee: {@code uk_order_order_sn} (V43) is
     * what actually enforces uniqueness, since any SELECT-then-INSERT can be raced. Bounded
     * so a pathological collision streak fails loudly instead of spinning forever — with
     * 10^6 values per day it should never be reached.
     */
    @Override
    public String generateOrderSn(LitemallUserId userId) {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyyMMdd");
        String now = df.format(LocalDate.now());
        for (int attempt = 0; attempt < 20; attempt++) {
            String orderSn = now + getRandomNum(6);
            if (countByOrderSn(orderSn) == 0) {
                return orderSn;
            }
        }
        throw new IllegalStateException(
                "Could not generate a unique order_sn for " + now + " after 20 attempts");
    }

    @Override
    public int count() {
        LitemallOrderExample example = new LitemallOrderExample();
        example.or().andDeletedEqualTo(false);
        return (int) litemallOrderMapper.countByExample(example);
    }

    @Override
    public List<LitemallOrderAggregate> findByPinkId(Integer pinkId) {
        LitemallOrderExample example = new LitemallOrderExample();
        example.or().andPinkIdEqualTo(pinkId).andDeletedEqualTo(false);
        return litemallOrderMapper.selectByExample(example)
                .stream().map(this::convertToDomainModel).collect(Collectors.toList());
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
    public int markPaidIfCreated(LitemallOrderId orderId, String payId, String paymentIntentId) {
        LocalDateTime now = LocalDateTime.now();
        LitemallOrder patch = new LitemallOrder();
        patch.setOrderStatus(LitemallOrderStatus.PAID.getCode());
        patch.setPayTime(now);
        patch.setUpdateTime(now);
        if (payId != null && !payId.isBlank()) {
            patch.setPayId(payId);
        }
        // Left NULL for wallet/offline tenders: the column is UNIQUE, and many NULLs are
        // allowed while many ''s would not be. updateByExampleSelective skips nulls anyway.
        if (paymentIntentId != null && !paymentIntentId.isBlank()) {
            patch.setPaymentIntentId(paymentIntentId);
        }

        LitemallOrderExample example = new LitemallOrderExample();
        example.createCriteria()
                .andIdEqualTo(orderId.getId())
                .andOrderStatusEqualTo(LitemallOrderStatus.CREATED.getCode());

        return litemallOrderMapper.updateByExampleSelective(patch, example);
    }

    @Override
    public int recordPaymentIntentIfCreated(LitemallOrderId orderId, String paymentIntentId) {
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            return 0;
        }
        LitemallOrder patch = new LitemallOrder();
        patch.setPaymentIntentId(paymentIntentId);
        patch.setUpdateTime(LocalDateTime.now());
        LitemallOrderExample example = new LitemallOrderExample();
        example.createCriteria()
                .andIdEqualTo(orderId.getId())
                .andOrderStatusEqualTo(LitemallOrderStatus.CREATED.getCode());
        return litemallOrderMapper.updateByExampleSelective(patch, example);
    }

    /**
     * Shared helper for the guarded transitions: apply {@code patch} only to a row of
     * {@code orderId} currently in one of {@code fromStatuses}. Returns rows updated
     * (1 = applied, 0 = the order had already moved on / a race lost).
     */
    private int conditionalTransition(LitemallOrderId orderId, LitemallOrder patch,
                                      LitemallOrderStatus... fromStatuses) {
        patch.setUpdateTime(LocalDateTime.now());
        List<Short> from = Arrays.stream(fromStatuses)
                .map(LitemallOrderStatus::getCode).collect(Collectors.toList());
        LitemallOrderExample example = new LitemallOrderExample();
        example.createCriteria()
                .andIdEqualTo(orderId.getId())
                .andOrderStatusIn(from);
        return litemallOrderMapper.updateByExampleSelective(patch, example);
    }

    @Override
    public int markCanceledIfCreated(LitemallOrderId orderId) {
        LitemallOrder patch = new LitemallOrder();
        patch.setOrderStatus(LitemallOrderStatus.CANCELED.getCode());
        patch.setEndTime(LocalDateTime.now());
        return conditionalTransition(orderId, patch, LitemallOrderStatus.CREATED);
    }

    @Override
    public int markSystemCanceledIfCreated(LitemallOrderId orderId) {
        LitemallOrder patch = new LitemallOrder();
        patch.setOrderStatus(LitemallOrderStatus.SYSTEM_CANCELED.getCode());
        patch.setEndTime(LocalDateTime.now());
        return conditionalTransition(orderId, patch, LitemallOrderStatus.CREATED);
    }

    @Override
    public int markDeliveredByWriteoff(LitemallOrderId orderId, String verifiedBy) {
        // Hand-written conditional UPDATE (db.dao.OrderMapper): 201 → 401 with
        // verify_time/verified_by stamped; WHERE order_status=201 AND verify_time IS NULL
        // makes a double scan a clean 0-row loss.
        return orderMapper.markDeliveredByWriteoff(orderId.getId(), verifiedBy);
    }

    @Override
    public int assignVerifyCode(LitemallOrderId orderId, String verifyCode) {
        return orderMapper.setVerifyCodeIfAbsent(orderId.getId(), verifyCode);
    }

    @Override
    public Optional<LitemallOrderAggregate> findByVerifyCode(String verifyCode) {
        if (verifyCode == null || verifyCode.isBlank()) {
            return Optional.empty();
        }
        LitemallOrderExample example = new LitemallOrderExample();
        example.createCriteria()
                .andVerifyCodeEqualTo(verifyCode)
                .andDeletedEqualTo(false);
        return Optional.ofNullable(convertToDomainModel(litemallOrderMapper.selectOneByExample(example)));
    }

    @Override
    public int markShippedIfPaid(LitemallOrderId orderId, String shipChannel, String shipSn, LocalDateTime shipTime) {
        LitemallOrder patch = new LitemallOrder();
        patch.setOrderStatus(LitemallOrderStatus.SHIPPED.getCode());
        patch.setShipChannel(shipChannel);
        patch.setShipSn(shipSn);
        patch.setShipTime(shipTime == null ? LocalDateTime.now() : shipTime);
        return conditionalTransition(orderId, patch, LitemallOrderStatus.PAID);
    }

    @Override
    public int updateShipTrackingIfShipped(LitemallOrderId orderId, String shipChannel, String shipSn) {
        LitemallOrder patch = new LitemallOrder();
        patch.setShipChannel(shipChannel);
        patch.setShipSn(shipSn);
        // No orderStatus on the patch: same guarded single-row UPDATE, status untouched.
        return conditionalTransition(orderId, patch, LitemallOrderStatus.SHIPPED);
    }

    @Override
    public int markDeliveredIfShipped(LitemallOrderId orderId, LocalDateTime confirmTime) {
        LitemallOrder patch = new LitemallOrder();
        patch.setOrderStatus(LitemallOrderStatus.DELIVERED.getCode());
        patch.setConfirmTime(confirmTime == null ? LocalDateTime.now() : confirmTime);
        return conditionalTransition(orderId, patch, LitemallOrderStatus.SHIPPED);
    }

    @Override
    public int markAutoDeliveredIfShipped(LitemallOrderId orderId, LocalDateTime confirmTime) {
        LitemallOrder patch = new LitemallOrder();
        patch.setOrderStatus(LitemallOrderStatus.AUTO_DELIVERED.getCode());
        patch.setConfirmTime(confirmTime == null ? LocalDateTime.now() : confirmTime);
        return conditionalTransition(orderId, patch, LitemallOrderStatus.SHIPPED);
    }

    @Override
    public int markRefundRequestedIfPayable(LitemallOrderId orderId, String refundContent) {
        LitemallOrder patch = new LitemallOrder();
        patch.setOrderStatus(LitemallOrderStatus.REFUND_REQUEST.getCode());
        if (refundContent != null) {
            patch.setRefundContent(refundContent);
        }
        // DELIVERED/AUTO_DELIVERED are included for the aftersale/RMA flow: a received
        // order can still be unwound (mirrors the enum's canTransitionTo).
        return conditionalTransition(orderId, patch,
                LitemallOrderStatus.PAID, LitemallOrderStatus.SHIPPED,
                LitemallOrderStatus.DELIVERED, LitemallOrderStatus.AUTO_DELIVERED);
    }

    @Override
    public int markRefundedIfRequested(LitemallOrderId orderId, java.math.BigDecimal refundAmount, LocalDateTime refundTime) {
        LitemallOrder patch = new LitemallOrder();
        patch.setOrderStatus(LitemallOrderStatus.REFUNDED.getCode());
        if (refundAmount != null) {
            patch.setRefundAmount(refundAmount);
        }
        patch.setRefundTime(refundTime == null ? LocalDateTime.now() : refundTime);
        patch.setEndTime(LocalDateTime.now());
        return conditionalTransition(orderId, patch, LitemallOrderStatus.REFUND_REQUEST);
    }

    @Override
    public int recordCjPlacement(LitemallOrderId orderId, String cjOrderId, String cjOrderNum, String shipChannel,
                                 String cjOrderStatus) {
        LitemallOrder patch = new LitemallOrder();
        patch.setId(orderId.getId());
        patch.setCjOrderId(cjOrderId);
        patch.setCjOrderNum(cjOrderNum);
        if (StringUtils.hasText(shipChannel)) {
            patch.setShipChannel(shipChannel); // the CJ logistics line; never touches freight_price
        }
        if (StringUtils.hasText(cjOrderStatus)) {
            patch.setCjOrderStatus(cjOrderStatus);
        }
        patch.setUpdateTime(LocalDateTime.now());
        return litemallOrderMapper.updateByPrimaryKeySelective(patch);
    }

    @Override
    public int updateCjOrderStatus(LitemallOrderId orderId, String cjOrderStatus) {
        LitemallOrder patch = new LitemallOrder();
        patch.setId(orderId.getId());
        patch.setCjOrderStatus(cjOrderStatus);
        patch.setUpdateTime(LocalDateTime.now());
        return litemallOrderMapper.updateByPrimaryKeySelective(patch);
    }

    @Override
    public List<LitemallOrderId> querySyncableCjOrders(int limit) {
        return orderMapper.selectSyncableCjOrderIds(limit).stream()
                .map(LitemallOrderId::new)
                .collect(Collectors.toList());
    }

    @Override
    public List<LitemallOrderId> queryPlaceableCjOrders(int limit) {
        return orderMapper.selectPlaceableCjOrderIds(limit).stream()
                .map(LitemallOrderId::new)
                .collect(Collectors.toList());
    }

    @Override
    public List<LitemallOrderId> queryApprovedPlaceableCjOrders(int limit) {
        return orderMapper.selectApprovedPlaceableCjOrderIds(limit).stream()
                .map(LitemallOrderId::new)
                .collect(Collectors.toList());
    }

    @Override
    public List<LitemallOrderAggregate> queryCjPlacementPending(int page, int limit) {
        int safeLimit = Math.max(1, limit);
        int offset = Math.max(0, page - 1) * safeLimit;
        return orderMapper.selectCjPlacementPending(offset, safeLimit).stream()
                .map(this::convertToDomainModel)
                .collect(Collectors.toList());
    }

    @Override
    public long countCjPlacementPending() {
        return orderMapper.countCjPlacementPending();
    }

    @Override
    public int stampCjPlacementApproval(LitemallOrderId orderId, String approvedBy) {
        return orderMapper.stampCjPlacementApproval(orderId.getId(), approvedBy);
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
        // Wave 7. Null-guarded because tax_price is NOT NULL DEFAULT 0.00: orders built by
        // paths that predate the tax seam must land as 0.00, not blow up on insert.
        dataModel.setTaxPrice(orderAggregate.getTaxPrice() == null
                ? java.math.BigDecimal.ZERO.setScale(2)
                : orderAggregate.getTaxPrice().getAmount());
        dataModel.setTaxBreakdown(orderAggregate.getTaxBreakdown());
        dataModel.setGrouponPrice(orderAggregate.getGrouponPrice().getAmount());
        dataModel.setOrderPrice(orderAggregate.getOrderPrice().getAmount());
        dataModel.setActualPrice(orderAggregate.getActualPrice().getAmount());

        dataModel.setPayId(orderAggregate.getPayId());
        dataModel.setPaymentIntentId(orderAggregate.getPaymentIntentId());
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

        dataModel.setAddressId(orderAggregate.getAddressId() == null
                ? null : orderAggregate.getAddressId().getId());
        dataModel.setCountryCode(orderAggregate.getCountryCode());
        dataModel.setSource(orderAggregate.getSource());
        dataModel.setCjOrderId(orderAggregate.getCjOrderId());
        dataModel.setCjOrderNum(orderAggregate.getCjOrderNum());
        dataModel.setCjOrderStatus(orderAggregate.getCjOrderStatus());
        dataModel.setCjLogisticName(orderAggregate.getCjLogisticName());
        // Wave 21 (V56): group-buy slot linkage.
        dataModel.setPinkId(orderAggregate.getPinkId());
        // Wave 23 (V59): CJ placement approval stamp.
        dataModel.setCjPlacementApprovedTime(orderAggregate.getCjPlacementApprovedTime());
        dataModel.setCjPlacementApprovedBy(orderAggregate.getCjPlacementApprovedBy());

        // In-store pickup / write-off (Wave 4, V35).
        dataModel.setDeliveryType(orderAggregate.getDeliveryType());
        dataModel.setStoreId(orderAggregate.getStoreId());
        dataModel.setVerifyCode(orderAggregate.getVerifyCode());
        dataModel.setVerifyTime(orderAggregate.getVerifyTime());
        dataModel.setVerifiedBy(orderAggregate.getVerifiedBy());

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
        // Map by numeric code, not enum NAME — the column stores the code (e.g. 101),
        // so valueOf("101") would throw "No enum constant ...101".
        domainModel.setOrderStatus(LitemallOrderStatus.fromCode(record.getOrderStatus()));
        domainModel.setAfterSaleStatus(LitemallAfterSaleStatus.fromStatusCode(record.getAftersaleStatus()));


        domainModel.setConsignee(record.getConsignee());
        domainModel.setMobile(record.getMobile());
        // The flattened shipping address comes from the ROW — reading it off the
        // fresh (empty) LitemallAddressAggregate above left it null on every load.
        domainModel.setAddress(record.getAddress());
        domainModel.setMessage(record.getMessage());

        domainModel.setGoodsPrice(new LitemallMoney(record.getGoodsPrice()));
        domainModel.setFreightPrice(new LitemallMoney(record.getFreightPrice()));
        domainModel.setCouponPrice(new LitemallMoney(record.getCouponPrice()));
        domainModel.setIntegralPrice(new LitemallMoney(record.getIntegralPrice()));
        // Rows written before V43 read back as 0.00 rather than a null that would NPE the
        // money chain (LitemallMoney rejects null).
        domainModel.setTaxPrice(new LitemallMoney(record.getTaxPrice() == null
                ? java.math.BigDecimal.ZERO
                : record.getTaxPrice()));
        domainModel.setTaxBreakdown(record.getTaxBreakdown());
        domainModel.setGrouponPrice(new LitemallMoney(record.getGrouponPrice()));
        domainModel.setOrderPrice(new LitemallMoney(record.getOrderPrice()));
        domainModel.setActualPrice(new LitemallMoney(record.getActualPrice()));
        domainModel.setPayId(record.getPayId());
        domainModel.setPaymentIntentId(record.getPaymentIntentId());
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

        domainModel.setAddressId(record.getAddressId() == null
                ? null : new LitemallAddressId(record.getAddressId()));
        domainModel.setCountryCode(record.getCountryCode());
        domainModel.setSource(record.getSource());
        domainModel.setCjOrderId(record.getCjOrderId());
        domainModel.setCjOrderNum(record.getCjOrderNum());
        domainModel.setCjOrderStatus(record.getCjOrderStatus());
        domainModel.setCjLogisticName(record.getCjLogisticName());
        // Wave 21 (V56): group-buy slot linkage.
        domainModel.setPinkId(record.getPinkId());
        // Wave 23 (V59): CJ placement approval stamp.
        domainModel.setCjPlacementApprovedTime(record.getCjPlacementApprovedTime());
        domainModel.setCjPlacementApprovedBy(record.getCjPlacementApprovedBy());

        // In-store pickup / write-off (Wave 4, V35).
        domainModel.setDeliveryType(record.getDeliveryType());
        domainModel.setStoreId(record.getStoreId());
        domainModel.setVerifyCode(record.getVerifyCode());
        domainModel.setVerifyTime(record.getVerifyTime());
        domainModel.setVerifiedBy(record.getVerifiedBy());

        return  domainModel;
    }
}
