package org.linlinjava.litemall.admin.service;

import com.github.binarywang.wxpay.bean.notify.WxPayNotifyResponse;
import com.github.binarywang.wxpay.bean.request.WxPayRefundRequest;
import com.github.binarywang.wxpay.bean.result.WxPayRefundResult;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.notify.NotifyService;
import org.linlinjava.litemall.core.notify.NotifyType;
import org.linlinjava.litemall.core.util.DateTimeUtil;
import org.linlinjava.litemall.core.util.JacksonUtil;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.*;
import org.linlinjava.litemall.db.service.*;
import org.linlinjava.litemall.db.util.CouponUserConstant;
import org.linlinjava.litemall.db.util.GrouponConstant;
import org.linlinjava.litemall.db.util.OrderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.linlinjava.litemall.admin.util.AdminResponseCode.*;
import static org.linlinjava.litemall.admin.util.AdminResponseCode.ORDER_PAY_FAILED;

@Service

public class AdminOrderService {
    private final Log logger = LogFactory.getLog(AdminOrderService.class);

    @Autowired
    private LitemallOrderGoodsService orderGoodsService;
    @Autowired
    private LitemallOrderService orderService;
    @Autowired
    private LitemallGoodsProductService productService;
    @Autowired
    private LitemallUserService userService;
    @Autowired
    private LitemallCommentService commentService;
    @Autowired
    private WxPayService wxPayService;
    @Autowired
    private NotifyService notifyService;
    @Autowired
    private LogHelper logHelper;
    @Autowired
    private LitemallCouponUserService couponUserService;

    public Object list(String nickname, String consignee, String orderSn, LocalDateTime start, LocalDateTime end, List<Short> orderStatusArray,
                       Integer page, Integer limit, String sort, String order) {
        Map<String, Object> data = (Map)orderService.queryVoSelective(nickname, consignee, orderSn, start, end, orderStatusArray, page, limit, sort, order);
        return ResponseUtil.ok(data);
    }

    public Object detail(Integer id) {
        LitemallOrder order = orderService.findById(id);
        List<LitemallOrderGoods> orderGoods = orderGoodsService.queryByOid(id);
        UserVo user = userService.findUserVoById(order.getUserId());
        Map<String, Object> data = new HashMap<>();
        data.put("order", order);
        data.put("orderGoods", orderGoods);
        data.put("user", user);

        return ResponseUtil.ok(data);
    }

    /**
     * Order Refund
     * <p>
     * 1. Check if the current order is eligible for refund;
     * 2. Perform WeChat refund operation;
     * 3. Set the order refund confirmation status;
     * 4. Return the order goods inventory to stock.
     * <p>
     * TODO
     * Although the WeChat refund API has been integrated, for security reasons,
     * it is recommended that developers delete the WeChat refund code here and adopt the following two-step process:
     * 1. Administrator logs into the WeChat official payment platform to perform the refund operation
     * 2. Administrator logs into the litemall management backend to perform order status modification and return goods inventory to stock
     *
     * @param body Order information, { orderId: xxx }
     * @return Result of the order refund operation
     */
    @Transactional
    public Object refund(String body) {
        Integer orderId = JacksonUtil.parseInteger(body, "orderId");
        String refundMoney = JacksonUtil.parseString(body, "refundMoney");
        if (orderId == null) {
            return ResponseUtil.badArgument();
        }
        if (StringUtils.isEmpty(refundMoney)) {
            return ResponseUtil.badArgument();
        }

        LitemallOrder order = orderService.findById(orderId);
        if (order == null) {
            return ResponseUtil.badArgument();
        }

        if (order.getActualPrice().compareTo(new BigDecimal(refundMoney)) != 0) {
            return ResponseUtil.badArgumentValue();
        }

        // If the order is not in refund status, it cannot be refunded
        if (!order.getOrderStatus().equals(OrderUtil.STATUS_REFUND)) {
            return ResponseUtil.fail(ORDER_CONFIRM_NOT_ALLOWED, "Order cannot be confirmed for receipt");
        }

        // WeChat refund
        WxPayRefundRequest wxPayRefundRequest = new WxPayRefundRequest();
        wxPayRefundRequest.setOutTradeNo(order.getOrderSn());
        wxPayRefundRequest.setOutRefundNo("refund_" + order.getOrderSn());
        // Yuanzhuan component
        Integer totalFee = order.getActualPrice().multiply(new BigDecimal(100)).intValue();
        wxPayRefundRequest.setTotalFee(totalFee);
        wxPayRefundRequest.setRefundFee(totalFee);

        WxPayRefundResult wxPayRefundResult;
        try {
            wxPayRefundResult = wxPayService.refund(wxPayRefundRequest);
        } catch (WxPayException e) {
            logger.error(e.getMessage(), e);
            return ResponseUtil.fail(ORDER_REFUND_FAILED, "Order refund failed");
        }
        if (!wxPayRefundResult.getReturnCode().equals("SUCCESS")) {
            logger.warn("refund fail: " + wxPayRefundResult.getReturnMsg());
            return ResponseUtil.fail(ORDER_REFUND_FAILED, "Order refund failed");
        }
        if (!wxPayRefundResult.getResultCode().equals("SUCCESS")) {
            logger.warn("refund fail: " + wxPayRefundResult.getReturnMsg());
            return ResponseUtil.fail(ORDER_REFUND_FAILED, "Order refund failed\n");
        }

        LocalDateTime now = LocalDateTime.now();
        // Set order cancellation status
        order.setOrderStatus(OrderUtil.STATUS_REFUND_CONFIRM);
        order.setEndTime(now);
        // Record order refund related information
        order.setRefundAmount(order.getActualPrice());
        order.setRefundType("WeChat refund interface\n");
        order.setRefundContent(wxPayRefundResult.getRefundId());
        order.setRefundTime(now);
        if (orderService.updateWithOptimisticLocker(order) == 0) {
            throw new RuntimeException("Update data has expired\n");
        }

        // Increase in quantity of goods
        List<LitemallOrderGoods> orderGoodsList = orderGoodsService.queryByOid(orderId);
        for (LitemallOrderGoods orderGoods : orderGoodsList) {
            Integer productId = orderGoods.getProductId();
            Short number = orderGoods.getNumber();
            if (productService.addStock(productId, number) == 0) {
                throw new RuntimeException("Product inventory increase failed\n");
            }
        }

        // Return coupon
        List<LitemallCouponUser> couponUsers = couponUserService.findByOid(orderId);
        for (LitemallCouponUser couponUser: couponUsers) {
            // Coupon status is set to available
            couponUser.setStatus(CouponUserConstant.STATUS_USABLE);
            couponUser.setUpdateTime(LocalDateTime.now());
            couponUserService.update(couponUser);
        }

        //TODO Send email and SMS notifications, asynchronous sending is used here
        // Notify the user of a successful refund,
        // for example, "The order refund you applied for [order number: {1}] has been successful, please wait patiently for the payment to arrive.”
        // Please note that only the last 6 digits of the order number are sent.
        notifyService.notifySmsTemplate(order.getMobile(), NotifyType.REFUND,
                new String[]{order.getOrderSn().substring(8, 14)});

        logHelper.logOrderSucceed("Refund", "order number " + order.getOrderSn());
        return ResponseUtil.ok();
    }

    /**
     * Shipping
     * 1. Check whether the current order can be shipped
     * 2. Set order shipping status
     *
     * @param body Order information，{ orderId：xxx, shipSn: xxx, shipChannel: xxx }
     * @return Order operation results
     * success rule { errno: 0, errmsg: '成功' }
     * Failure { errno: XXX, errmsg: XXX }
     */
    public Object ship(String body) {
        Integer orderId = JacksonUtil.parseInteger(body, "orderId");
        String shipSn = JacksonUtil.parseString(body, "shipSn");
        String shipChannel = JacksonUtil.parseString(body, "shipChannel");
        if (orderId == null || shipSn == null || shipChannel == null) {
            return ResponseUtil.badArgument();
        }

        LitemallOrder order = orderService.findById(orderId);
        if (order == null) {
            return ResponseUtil.badArgument();
        }

        // If the order is not in paid status，cannot be shipped
        if (!order.getOrderStatus().equals(OrderUtil.STATUS_PAY)) {
            return ResponseUtil.fail(ORDER_CONFIRM_NOT_ALLOWED, "Order cannot be confirmed for receipt");
        }

        order.setOrderStatus(OrderUtil.STATUS_SHIP);
        order.setShipSn(shipSn);
        order.setShipChannel(shipChannel);
        order.setShipTime(LocalDateTime.now());
        if (orderService.updateWithOptimisticLocker(order) == 0) {
            return ResponseUtil.updatedDateExpired();
        }

        //TODO Send email and SMS notifications，Asynchronous sending is used here
        // A notification text message will be sent to the user when the shipment is shipped.:          *
        // "Your order has been shipped，courier company {1}，Express order {2} ，Please pay attention to check"
        notifyService.notifySmsTemplate(order.getMobile(), NotifyType.SHIP, new String[]{shipChannel, shipSn});

        logHelper.logOrderSucceed("Shipping", "order number " + order.getOrderSn());
        return ResponseUtil.ok();
    }

    /**
     * Delete order
     * 1. Check whether the current order can be deleted
     * 2. Delete order
     *
     * @param body Order information，{ orderId：xxx }
     * @return Order operation results
     * success rule { errno: 0, errmsg: '成功' }
     * Failure { errno: XXX, errmsg: XXX }
     */
    public Object delete(String body) {
        Integer orderId = JacksonUtil.parseInteger(body, "orderId");
        LitemallOrder order = orderService.findById(orderId);
        if (order == null) {
            return ResponseUtil.badArgument();
        }

        // 如果订单不是关闭状态(已取消、系统取消、已退款、用户已确认、系统已确认)，则不能删除
        Short status = order.getOrderStatus();
        if (!status.equals(OrderUtil.STATUS_CANCEL) && !status.equals(OrderUtil.STATUS_AUTO_CANCEL) &&
                !status.equals(OrderUtil.STATUS_CONFIRM) &&!status.equals(OrderUtil.STATUS_AUTO_CONFIRM) &&
                !status.equals(OrderUtil.STATUS_REFUND_CONFIRM)) {
            return ResponseUtil.fail(ORDER_DELETE_FAILED, "Order cannot be deleted");
        }
        // Delete order
        orderService.deleteById(orderId);
        // Delete order items
        orderGoodsService.deleteByOrderId(orderId);
        logHelper.logOrderSucceed("delete", "order number " + order.getOrderSn());
        return ResponseUtil.ok();
    }

    /**
     * Reply to order items
     *
     * @param body Order information，{ orderId：xxx }
     * @return Order operation results
     * success rule { errno: 0, errmsg: 'success' }
     * Failure { errno: XXX, errmsg: XXX }
     */
    public Object reply(String body) {
        Integer commentId = JacksonUtil.parseInteger(body, "commentId");
        if (commentId == null || commentId == 0) {
            return ResponseUtil.badArgument();
        }
        // 目前只支持回复一次
        LitemallComment comment = commentService.findById(commentId);
        if(comment == null){
            return ResponseUtil.badArgument();
        }
        if (!StringUtils.isEmpty(comment.getAdminContent())) {
            return ResponseUtil.fail(ORDER_REPLY_EXIST, "The order has been replied！");
        }
        String content = JacksonUtil.parseString(body, "content");
        if (StringUtils.isEmpty(content)) {
            return ResponseUtil.badArgument();
        }
        // 更新评价回复
        comment.setAdminContent(content);
        commentService.updateById(comment);

        return ResponseUtil.ok();
    }

    public Object pay(String body) {
        Integer orderId = JacksonUtil.parseInteger(body, "orderId");
        String newMoney = JacksonUtil.parseString(body, "newMoney");

        if (orderId == null || StringUtils.isEmpty(newMoney)) {
            return ResponseUtil.badArgument();
        }
        BigDecimal actualPrice = new BigDecimal(newMoney);

        LitemallOrder order = orderService.findById(orderId);
        if (order == null) {
            return ResponseUtil.badArgument();
        }
        if (!order.getOrderStatus().equals(OrderUtil.STATUS_CREATE)) {
            return ResponseUtil.fail(ORDER_PAY_FAILED, "The current order status does not support offline collection.");
        }

        order.setActualPrice(actualPrice);
        order.setOrderStatus(OrderUtil.STATUS_PAY);
        if (orderService.updateWithOptimisticLocker(order) == 0) {
            return WxPayNotifyResponse.fail("Update data has expired");
        }

        return ResponseUtil.ok();
    }
}
