package org.linlinjava.litemall.wx.service;

import com.github.binarywang.wxpay.bean.notify.WxPayNotifyResponse;
import com.github.binarywang.wxpay.bean.notify.WxPayOrderNotifyResult;
import com.github.binarywang.wxpay.bean.order.WxPayMpOrderResult;
import com.github.binarywang.wxpay.bean.order.WxPayMwebOrderResult;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderRequest;
import com.github.binarywang.wxpay.bean.result.BaseWxPayResult;
import com.github.binarywang.wxpay.constant.WxPayConstants;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.express.ExpressService;
import org.linlinjava.litemall.core.express.dao.ExpressInfo;
import org.linlinjava.litemall.core.notify.NotifyService;
import org.linlinjava.litemall.core.notify.NotifyType;
import org.linlinjava.litemall.core.qcode.QCodeService;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.core.task.TaskService;
import org.linlinjava.litemall.core.util.DateTimeUtil;
import org.linlinjava.litemall.core.util.JacksonUtil;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.*;
import org.linlinjava.litemall.db.service.*;
import org.linlinjava.litemall.db.util.CouponUserConstant;
import org.linlinjava.litemall.db.util.GrouponConstant;
import org.linlinjava.litemall.db.util.OrderHandleOption;
import org.linlinjava.litemall.db.util.OrderUtil;
import org.linlinjava.litemall.core.util.IpUtil;
import org.linlinjava.litemall.wx.task.OrderUnpaidTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;


import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.linlinjava.litemall.wx.util.WxResponseCode.*;

/**
 * Order service
 *
 * <p>
 *  Order status:
 * 101: The order is GENERATED and not PAID; 102: the user cancels the order without payment after placing the order; 103: the system automatically cancels the order after the unpaid timeout occurs.
 * 201: Payment is COMPLETED, but the merchant has not shipped the goods; 202: Order production, the payment has been made but the goods have not been shipped, but the refund is cancelled;
 * 301: Merchant SHIPPED, user not confirmed;
 * 401: The user confirms the receipt; 402: If the user does not confirm the receipt for more than a certain period of time, then the system automatically confirms the receipt;
 *
 * <p>
 *  User actions:
 * When 101: User fails to pay, At this time, the operations that the user can perform are canceling the order or making payment.
 * When 201: Payment is completed but the merchant has not shipped the goods, At this point the user can cancel the order and apply for a refund
 * When 301: Merchant has shipped the goods, At this point, the user can confirm the receipt of the goods.
 * When 401: After the user confirms receipt of the goods, The operation that the user can perform at this time is to delete the order, Rate product, Apply for after-sales, Or buy again
 * When 402: After the system automatically confirms receipt of the goods, At this point the user can delete the order, Rate product, Apply for after-sales, Or buy again
 */
@Service
public class WxOrderService {
    private final Log logger = LogFactory.getLog(WxOrderService.class);

    @Autowired
    private LitemallUserService userService;
    @Autowired
    private LitemallOrderService orderService;
    @Autowired
    private LitemallOrderGoodsService orderGoodsService;
    @Autowired
    private LitemallAddressService addressService;
    @Autowired
    private LitemallCartService cartService;
    @Autowired
    private LitemallRegionService regionService;
    @Autowired
    private LitemallGoodsProductService productService;
    @Autowired
    private WxPayService wxPayService;
    @Autowired
    private NotifyService notifyService;
    @Autowired
    private LitemallGrouponRulesService grouponRulesService;
    @Autowired
    private LitemallGrouponService grouponService;
    @Autowired
    private QCodeService qCodeService;
    @Autowired
    private ExpressService expressService;
    @Autowired
    private LitemallCommentService commentService;
    @Autowired
    private LitemallCouponService couponService;
    @Autowired
    private LitemallCouponUserService couponUserService;
    @Autowired
    private CouponVerifyService couponVerifyService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private LitemallAftersaleService aftersaleService;

    /**
     * Order list
     *
     * @param userId user ID
     * @param showType order information:
     * 0, All orders;
     * 1, PENDING payment;
     * 2, AWAITING shipment;
     * 3, AWAITING receipt;
     * 4. AWAITING evaluation.
     * @param page Number of pagination pages
     * @param limit Paging size
     * @return order list
     */
    public Object list(Integer userId, Integer showType, Integer page, Integer limit, String sort, String order) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        List<Short> orderStatus = OrderUtil.orderStatus(showType);
        List<LitemallOrder> orderList = orderService.queryByOrderStatus(userId, orderStatus, page, limit, sort, order);

        List<Map<String, Object>> orderVoList = new ArrayList<>(orderList.size());
        for (LitemallOrder o : orderList) {
            Map<String, Object> orderVo = new HashMap<>();
            orderVo.put("id", o.getId());
            orderVo.put("orderSn", o.getOrderSn());
            orderVo.put("actualPrice", o.getActualPrice());
            orderVo.put("orderStatusText", OrderUtil.orderStatusText(o));
            orderVo.put("handleOption", OrderUtil.build(o));
            orderVo.put("aftersaleStatus", o.getAftersaleStatus());

            LitemallGroupon groupon = grouponService.queryByOrderId(o.getId());
            if (groupon != null) {
                orderVo.put("isGroupin", true);
            } else {
                orderVo.put("isGroupin", false);
            }

            List<LitemallOrderGoods> orderGoodsList = orderGoodsService.queryByOid(o.getId());
            List<Map<String, Object>> orderGoodsVoList = new ArrayList<>(orderGoodsList.size());
            for (LitemallOrderGoods orderGoods : orderGoodsList) {
                Map<String, Object> orderGoodsVo = new HashMap<>();
                orderGoodsVo.put("id", orderGoods.getId());
                orderGoodsVo.put("goodsName", orderGoods.getGoodsName());
                orderGoodsVo.put("number", orderGoods.getNumber());
                orderGoodsVo.put("picUrl", orderGoods.getPicUrl());
                orderGoodsVo.put("specifications", orderGoods.getSpecifications());
                orderGoodsVo.put("price",orderGoods.getPrice());
                orderGoodsVoList.add(orderGoodsVo);
            }
            orderVo.put("goodsList", orderGoodsVoList);

            orderVoList.add(orderVo);
        }

        return ResponseUtil.okList(orderVoList, orderList);
    }

    /**
     *Order details
     *
     * @param userId user ID
     * @param orderId order ID
     * @return order details
     */
    public Object detail(Integer userId, Integer orderId) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        // 订单信息
        LitemallOrder order = orderService.findById(userId, orderId);
        if (null == order) {
            return ResponseUtil.fail(ORDER_UNKNOWN, "Order does not exist");
        }
        if (!order.getUserId().equals(userId)) {
            return ResponseUtil.fail(ORDER_INVALID, "Not the current user’s order");
        }
        Map<String, Object> orderVo = new HashMap<String, Object>();
        orderVo.put("id", order.getId());
        orderVo.put("orderSn", order.getOrderSn());
        orderVo.put("message", order.getMessage());
        orderVo.put("addTime", order.getAddTime());
        orderVo.put("consignee", order.getConsignee());
        orderVo.put("mobile", order.getMobile());
        orderVo.put("address", order.getAddress());
        orderVo.put("goodsPrice", order.getGoodsPrice());
        orderVo.put("couponPrice", order.getCouponPrice());
        orderVo.put("freightPrice", order.getFreightPrice());
        orderVo.put("actualPrice", order.getActualPrice());
        orderVo.put("orderStatusText", OrderUtil.orderStatusText(order));
        orderVo.put("handleOption", OrderUtil.build(order));
        orderVo.put("aftersaleStatus", order.getAftersaleStatus());
        orderVo.put("expCode", order.getShipChannel());
        orderVo.put("expName", expressService.getVendorName(order.getShipChannel()));
        orderVo.put("expNo", order.getShipSn());

        List<LitemallOrderGoods> orderGoodsList = orderGoodsService.queryByOid(order.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("orderInfo", orderVo);
        result.put("orderGoods", orderGoodsList);

        // The order status is shipped and the logistics information is not empty.
        //"YTO", "800669400640887922"
        if (order.getOrderStatus().equals(OrderUtil.STATUS_SHIP)) {
            ExpressInfo ei = expressService.getExpressInfo(order.getShipChannel(), order.getShipSn());
            if(ei == null){
                result.put("expressInfo", new ArrayList<>());
            }
            else {
                result.put("expressInfo", ei);
            }
        }
        else{
            result.put("expressInfo", new ArrayList<>());
        }

        return ResponseUtil.ok(result);

    }

    /**
     * Submit order
     * <p>
     * 1. Create order table items and order product table items;
     * 2. Shopping cart empty;
     * 3. The coupon setting has been used;
     * 4. Product inventory reduction;
     * 5. If it is a group purchase product，Then create a group buying activity table item。
     *
     * @param userId User ID
     * @param body   Order information，{ cartId：xxx, addressId: xxx, couponId: xxx, message: xxx, grouponRulesId: xxx,  grouponLinkId: xxx}
     * @return Submit order operation results
     */
    @Transactional
    public Object submit(Integer userId, String body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        if (body == null) {
            return ResponseUtil.badArgument();
        }
        Integer cartId = JacksonUtil.parseInteger(body, "cartId");
        Integer addressId = JacksonUtil.parseInteger(body, "addressId");
        Integer couponId = JacksonUtil.parseInteger(body, "couponId");
        Integer userCouponId = JacksonUtil.parseInteger(body, "userCouponId");
        String message = JacksonUtil.parseString(body, "message");
        Integer grouponRulesId = JacksonUtil.parseInteger(body, "grouponRulesId");
        Integer grouponLinkId = JacksonUtil.parseInteger(body, "grouponLinkId");

        //If it is a group purchase project, verify whether the activity is valid
        if (grouponRulesId != null && grouponRulesId > 0) {
            LitemallGrouponRules rules = grouponRulesService.findById(grouponRulesId);
            //Record not found
            if (rules == null) {
                return ResponseUtil.badArgument();
            }
            //Group buying rules have expired
            if (rules.getStatus().equals(GrouponConstant.RULE_STATUS_DOWN_EXPIRE)) {
                return ResponseUtil.fail(GROUPON_EXPIRED, "Group purchase has expired!");
            }
            //Group buying rules have been offline
            if (rules.getStatus().equals(GrouponConstant.RULE_STATUS_DOWN_ADMIN)) {
                return ResponseUtil.fail(GROUPON_OFFLINE, "Group buying has been offline!");
            }

            if (grouponLinkId != null && grouponLinkId > 0) {
                //Group purchase is full
                if(grouponService.countGroupon(grouponLinkId) >= (rules.getDiscountMember() - 1)){
                    return ResponseUtil.fail(GROUPON_FULL, "The group buying event is full!");
                }
                // NOTE
                // The business aspect here allows users to start groups multiple times，and participated in many tours，
                // But it will limit the following two points：
                // （1）Not allowed to participate in group purchases that have already been joined
                if(grouponService.hasJoin(userId, grouponLinkId)){
                    return ResponseUtil.fail(GROUPON_JOIN, "Already participated in the group buying event!");
                }
                // （2）Not allowed to participate in group buying organized by oneself
                LitemallGroupon groupon = grouponService.queryById(userId, grouponLinkId);
                // if(groupon.getCreatorUserId().equals(userId)){
                //     return ResponseUtil.fail(GROUPON_JOIN, "Already participated in the group buying event!");
                // }
                if(groupon!=null) {
	                if(groupon.getCreatorUserId().equals(userId)){
	                    return ResponseUtil.fail(GROUPON_JOIN, "Already participated in the group buying event!");
	                }
                }
            }
        }

        if (cartId == null || addressId == null || couponId == null) {
            return ResponseUtil.badArgument();
        }

        // Shipping address
        LitemallAddress checkedAddress = addressService.query(userId, addressId);
        if (checkedAddress == null) {
            return ResponseUtil.badArgument();
        }

        // Group purchase discount
        BigDecimal grouponPrice = new BigDecimal(0);
        LitemallGrouponRules grouponRules = grouponRulesService.findById(grouponRulesId);
        if (grouponRules != null) {
            grouponPrice = grouponRules.getDiscount();
        }

        // Product price
        List<LitemallCart> checkedGoodsList = null;
        if (cartId.equals(0)) {
            checkedGoodsList = cartService.queryByUidAndChecked(userId);
        } else {
            LitemallCart cart = cartService.findById(cartId);
            checkedGoodsList = new ArrayList<>(1);
            checkedGoodsList.add(cart);
        }
        if (checkedGoodsList.isEmpty()) {
            return ResponseUtil.badArgumentValue();
        }
        BigDecimal checkedGoodsPrice = new BigDecimal(0);
        for (LitemallCart checkGoods : checkedGoodsList) {
            //  Only when the product ID meets the group purchase specifications will the group purchase discount be available
            if (grouponRules != null && grouponRules.getGoodsId().equals(checkGoods.getGoodsId())) {
                checkedGoodsPrice = checkedGoodsPrice.add(checkGoods.getPrice().subtract(grouponPrice).multiply(new BigDecimal(checkGoods.getNumber())));
            } else {
                checkedGoodsPrice = checkedGoodsPrice.add(checkGoods.getPrice().multiply(new BigDecimal(checkGoods.getNumber())));
            }
        }

        // Get available coupon information
        // Amount reduced using coupons
        BigDecimal couponPrice = new BigDecimal(0);
        // if couponId=0 : There is no coupon，
        // if couponId=-1 : Do not use coupons
        if (couponId != 0 && couponId != -1) {
            LitemallCoupon coupon = couponVerifyService.checkCoupon(userId, couponId, userCouponId, checkedGoodsPrice, checkedGoodsList);
            if (coupon == null) {
                return ResponseUtil.badArgumentValue();
            }
            couponPrice = coupon.getDiscount();
        }


        // Calculate shipping costs based on total order price，
        // Meet the conditions (e.g. $88), free shipping，Otherwise you need to pay shipping fee（For example, $8）；
        BigDecimal freightPrice = new BigDecimal(0);
        if (checkedGoodsPrice.compareTo(SystemConfig.getFreightLimit()) < 0) {
            freightPrice = SystemConfig.getFreight();
        }

        // Other money available，For example, user points
        BigDecimal integralPrice = new BigDecimal(0);

        // Order fee
        BigDecimal orderTotalPrice = checkedGoodsPrice.add(freightPrice).subtract(couponPrice).max(new BigDecimal(0));
        // final payment
        BigDecimal actualPrice = orderTotalPrice.subtract(integralPrice);

        Integer orderId = null;
        LitemallOrder order = null;
        // Order
        order = new LitemallOrder();
        order.setUserId(userId);
        order.setOrderSn(orderService.generateOrderSn(userId));
        order.setOrderStatus(OrderUtil.STATUS_CREATE);
        order.setConsignee(checkedAddress.getName());
        order.setMobile(checkedAddress.getTel());
        order.setMessage(message);
        String detailedAddress = checkedAddress.getProvince() + checkedAddress.getCity() + checkedAddress.getCounty() + " " + checkedAddress.getAddressDetail();
        order.setAddress(detailedAddress);
        order.setGoodsPrice(checkedGoodsPrice);
        order.setFreightPrice(freightPrice);
        order.setCouponPrice(couponPrice);
        order.setIntegralPrice(integralPrice);
        order.setOrderPrice(orderTotalPrice);
        order.setActualPrice(actualPrice);

        // There is group buying
        if (grouponRules != null) {
            order.setGrouponPrice(grouponPrice);    //  Group purchase price

        } else {
            order.setGrouponPrice(new BigDecimal(0));    //  团购价格
        }

        // Add order form item
        orderService.add(order);
        orderId = order.getId();

        // Add order item items
        for (LitemallCart cartGoods : checkedGoodsList) {
            // Order items
            LitemallOrderGoods orderGoods = new LitemallOrderGoods();
            orderGoods.setOrderId(order.getId());
            orderGoods.setGoodsId(cartGoods.getGoodsId());
            orderGoods.setGoodsSn(cartGoods.getGoodsSn());
            orderGoods.setProductId(cartGoods.getProductId());
            orderGoods.setGoodsName(cartGoods.getGoodsName());
            orderGoods.setPicUrl(cartGoods.getPicUrl());
            orderGoods.setPrice(cartGoods.getPrice());
            orderGoods.setNumber(cartGoods.getNumber());
            orderGoods.setSpecifications(cartGoods.getSpecifications());
            orderGoods.setAddTime(LocalDateTime.now());

            orderGoodsService.add(orderGoods);
        }

        // Delete product information in shopping cart
        if(cartId.equals(0)){
            cartService.clearGoods(userId);
        }else{
            cartService.deleteById(cartId);
        }

        // Product quantity reduced
        for (LitemallCart checkGoods : checkedGoodsList) {
            Integer productId = checkGoods.getProductId();
            LitemallGoodsProduct product = productService.findById(productId);

            int remainNumber = product.getNumber() - checkGoods.getNumber();
            if (remainNumber < 0) {
                throw new RuntimeException("The quantity of the ordered product is greater than the inventory");
            }
            if (productService.reduceStock(productId, checkGoods.getNumber()) == 0) {
                throw new RuntimeException("Product inventory reduction failed");
            }
        }

        // If a coupon is used，Set coupon usage status
        if (couponId != 0 && couponId != -1) {
            LitemallCouponUser couponUser = couponUserService.findById(userCouponId);
            couponUser.setStatus(CouponUserConstant.STATUS_USED);
            couponUser.setUsedTime(LocalDateTime.now());
            couponUser.setOrderId(orderId);
            couponUserService.update(couponUser);
        }

        //If it is a group purchase project，Add group buying information
        if (grouponRulesId != null && grouponRulesId > 0) {
            LitemallGroupon groupon = new LitemallGroupon();
            groupon.setOrderId(orderId);
            groupon.setStatus(GrouponConstant.STATUS_NONE);
            groupon.setUserId(userId);
            groupon.setRulesId(grouponRulesId);

            //participants
            if (grouponLinkId != null && grouponLinkId > 0) {
                //Participated group buying records
                LitemallGroupon baseGroupon = grouponService.queryById(grouponLinkId);
                groupon.setCreatorUserId(baseGroupon.getCreatorUserId());
                groupon.setGrouponId(grouponLinkId);
                groupon.setShareUrl(baseGroupon.getShareUrl());
                grouponService.createGroupon(groupon);
            } else {
                groupon.setCreatorUserId(userId);
                groupon.setCreatorUserTime(LocalDateTime.now());
                groupon.setGrouponId(0);
                grouponService.createGroupon(groupon);
                grouponLinkId = groupon.getId();
            }
        }

        // NOTE: It is recommended that developers verify the following code from the business scenario，
        //              Prevent users from using business bugs to make orders skip the payment process
        // If the actual payment fee for the order is 0，
        //      Then directly skip the payment and become the status of pending shipment.
        boolean payed = false;
        if (order.getActualPrice().equals(new BigDecimal("0.00"))) {
            payed = true;

            LitemallOrder o = new LitemallOrder();
            o.setId(orderId);
            o.setOrderStatus(OrderUtil.STATUS_PAY);
            orderService.updateSelective(o);

            //  Payment successful，There is group buying information，Update group buying information
            LitemallGroupon groupon = grouponService.queryByOrderId(order.getId());
            if (groupon != null) {
                grouponRules = grouponRulesService.findById(groupon.getRulesId());

                //Shared images are created only if the originator
                if (groupon.getGrouponId() == 0) {
                    String url = qCodeService.createGrouponShareImage(grouponRules.getGoodsName(), grouponRules.getPicUrl(), groupon);
                    groupon.setShareUrl(url);
                }
                groupon.setStatus(GrouponConstant.STATUS_ON);
                if (grouponService.updateById(groupon) == 0) {
                    throw new RuntimeException("Update data has expired");
                }


                List<LitemallGroupon> grouponList = grouponService.queryJoinRecord(groupon.getGrouponId());
                if (groupon.getGrouponId() != 0 && (grouponList.size() >= grouponRules.getDiscountMember() - 1)) {
                    for (LitemallGroupon grouponActivity : grouponList) {
                        grouponActivity.setStatus(GrouponConstant.STATUS_SUCCEED);
                        grouponService.updateById(grouponActivity);
                    }

                    LitemallGroupon grouponSource = grouponService.queryById(groupon.getGrouponId());
                    grouponSource.setStatus(GrouponConstant.STATUS_SUCCEED);
                    grouponService.updateById(grouponSource);
                }
            }

            //TODO Send email and SMS notifications，Asynchronous sending is used here
            //          After the order payment is successful，
            //          A text message will be sent to the user，and send an email to the administrator
            notifyService.notifyMail("New order notification", order.toString());
            // Here, WeChat’s SMS platform has restrictions on parameter length，
            //      Therefore, only the last 6 digits of the order number are truncated.
            notifyService.notifySmsTemplateSync(order.getMobile(), NotifyType.PAY_SUCCEED, new String[]{order.getOrderSn().substring(8, 14)});
        }
        else {
            // Order payment overdue task
            taskService.addTask(new OrderUnpaidTask(orderId));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("payed", payed);
        if (grouponRulesId != null && grouponRulesId > 0) {
            data.put("grouponLinkId", grouponLinkId);
        }
        else {
            data.put("grouponLinkId", 0);
        }
        return ResponseUtil.ok(data);
    }

    /**
     * Cancel order
     * <p>
     * 1. Check whether the current order can be canceled;
     * 2. Set order cancellation status;
     * 3. Product inventory restoration;
     * 4. Return coupons;
     *
     * @param userId user ID
     * @param body order information, { orderId: xxx }
     * @return Cancel order operation result
     */
    @Transactional
    public Object cancel(Integer userId, String body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Integer orderId = JacksonUtil.parseInteger(body, "orderId");
        if (orderId == null) {
            return ResponseUtil.badArgument();
        }

        LitemallOrder order = orderService.findById(userId, orderId);
        if (order == null) {
            return ResponseUtil.badArgumentValue();
        }
        if (!order.getUserId().equals(userId)) {
            return ResponseUtil.badArgumentValue();
        }

        LocalDateTime preUpdateTime = order.getUpdateTime();

        // Check whether it can be canceled
        OrderHandleOption handleOption = OrderUtil.build(order);
        if (!handleOption.isCancel()) {
            return ResponseUtil.fail(ORDER_INVALID_OPERATION, "Order cannot be canceled");
        }

        // Set order canceled status
        order.setOrderStatus(OrderUtil.STATUS_CANCEL);
        order.setEndTime(LocalDateTime.now());
        if (orderService.updateWithOptimisticLocker(order) == 0) {
            throw new RuntimeException("Update data has expired");
        }

        // Increase in quantity of goods
        List<LitemallOrderGoods> orderGoodsList = orderGoodsService.queryByOid(orderId);
        for (LitemallOrderGoods orderGoods : orderGoodsList) {
            Integer productId = orderGoods.getProductId();
            Short number = orderGoods.getNumber();
            if (productService.addStock(productId, number) == 0) {
                throw new RuntimeException("Product inventory increase failed");
            }
        }

        // Return coupon
        releaseCoupon(orderId);

        return ResponseUtil.ok();
    }

    /**
     * The prepayment session ID of the payment order
     * <p>
     * 1. Check whether the current order can be paid
     * 2. WeChat merchant platform returns payment order ID
     * 3. Set order payment status
     *
     * @param userId user ID
     * @param body order information, { orderId: xxx }
     * @return payment order ID
     */
    @Transactional
    public Object prepay(Integer userId, String body, HttpServletRequest request) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Integer orderId = JacksonUtil.parseInteger(body, "orderId");
        if (orderId == null) {
            return ResponseUtil.badArgument();
        }

        LitemallOrder order = orderService.findById(userId, orderId);
        if (order == null) {
            return ResponseUtil.badArgumentValue();
        }
        if (!order.getUserId().equals(userId)) {
            return ResponseUtil.badArgumentValue();
        }

        // Check whether it can be canceled
        OrderHandleOption handleOption = OrderUtil.build(order);
        if (!handleOption.isPay()) {
            return ResponseUtil.fail(ORDER_INVALID_OPERATION, "Order cannot be paid");
        }

        LitemallUser user = userService.findById(userId);
        String openid = user.getWeixinOpenid();
        if (openid == null) {
            return ResponseUtil.fail(AUTH_OPENID_UNACCESS, "Order cannot be paid");
        }
        WxPayMpOrderResult result = null;
        try {
            WxPayUnifiedOrderRequest orderRequest = new WxPayUnifiedOrderRequest();
            orderRequest.setOutTradeNo(order.getOrderSn());
            orderRequest.setOpenid(openid);
            orderRequest.setBody("Order：" + order.getOrderSn());
            // Yuanzhuan component
            int fee = 0;
            BigDecimal actualPrice = order.getActualPrice();
            fee = actualPrice.multiply(new BigDecimal(100)).intValue();
            orderRequest.setTotalFee(fee);
            orderRequest.setSpbillCreateIp(IpUtil.getIpAddr(request));

            result = wxPayService.createOrder(orderRequest);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseUtil.fail(ORDER_PAY_FAIL, "Order cannot be paid");
        }

        if (orderService.updateWithOptimisticLocker(order) == 0) {
            return ResponseUtil.updatedDateExpired();
        }
        return ResponseUtil.ok(result);
    }

    /**
     * WeChat H5 payment
     *
     * @param userId
     * @param body
     * @param request
     * @return
     */
    @Transactional
    public Object h5pay(Integer userId, String body, HttpServletRequest request) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Integer orderId = JacksonUtil.parseInteger(body, "orderId");
        if (orderId == null) {
            return ResponseUtil.badArgument();
        }

        LitemallOrder order = orderService.findById(userId, orderId);
        if (order == null) {
            return ResponseUtil.badArgumentValue();
        }
        if (!order.getUserId().equals(userId)) {
            return ResponseUtil.badArgumentValue();
        }

        // Check whether it can be canceled
        OrderHandleOption handleOption = OrderUtil.build(order);
        if (!handleOption.isPay()) {
            return ResponseUtil.fail(ORDER_INVALID_OPERATION, "Order cannot be paid");
        }

        WxPayMwebOrderResult result = null;
        try {
            WxPayUnifiedOrderRequest orderRequest = new WxPayUnifiedOrderRequest();
            orderRequest.setOutTradeNo(order.getOrderSn());
            orderRequest.setTradeType("MWEB");
            orderRequest.setBody("Order：" + order.getOrderSn());
            // Yuanzhuan component
            int fee = 0;
            BigDecimal actualPrice = order.getActualPrice();
            fee = actualPrice.multiply(new BigDecimal(100)).intValue();
            orderRequest.setTotalFee(fee);
            orderRequest.setSpbillCreateIp(IpUtil.getIpAddr(request));

            result = wxPayService.createOrder(orderRequest);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseUtil.ok(result);
    }

    /**
     * WeChat payment success or failure callback interface
     * <p>
     * 1. Check whether the current order is in payment status;
     * 2. Set information related to order payment success status;
     * 3. Respond to WeChat merchant platform.
     *
     * @param request request content
     * @param response response content
     * @return operation result
     */
    @Transactional
    public Object payNotify(HttpServletRequest request, HttpServletResponse response) {
        String xmlResult = null;
        try {
            xmlResult = IOUtils.toString(request.getInputStream(), request.getCharacterEncoding());
        } catch (IOException e) {
            e.printStackTrace();
            return WxPayNotifyResponse.fail(e.getMessage());
        }

        WxPayOrderNotifyResult result = null;
        try {
            result = wxPayService.parseOrderNotifyResult(xmlResult);

            if(!WxPayConstants.ResultCode.SUCCESS.equals(result.getResultCode())){
                logger.error(xmlResult);
                throw new WxPayException("WeChat notification payment failed！");
            }
            if(!WxPayConstants.ResultCode.SUCCESS.equals(result.getReturnCode())){
                logger.error(xmlResult);
                throw new WxPayException("WeChat notification payment failed！");
            }
        } catch (WxPayException e) {
            e.printStackTrace();
            return WxPayNotifyResponse.fail(e.getMessage());
        }

        logger.info("Process order payments on Tencent payment platform");
        logger.info(result);

        String orderSn = result.getOutTradeNo();
        String payId = result.getTransactionId();

        // converted into yuan
        String totalFee = BaseWxPayResult.fenToYuan(result.getTotalFee());
        LitemallOrder order = orderService.findBySn(orderSn);
        if (order == null) {
            return WxPayNotifyResponse.fail("Order does not exist sn=" + orderSn);
        }

        // Check if this order has been processed
        if (OrderUtil.hasPayed(order)) {
            return WxPayNotifyResponse.success("The order has been processed successfully!");
        }

        // Check payment order amount
        if (!totalFee.equals(order.getActualPrice().toString())) {
            return WxPayNotifyResponse.fail(order.getOrderSn() + " : The payment amount does not meet the totalFee=" + totalFee);
        }

        order.setPayId(payId);
        order.setPayTime(LocalDateTime.now());
        order.setOrderStatus(OrderUtil.STATUS_PAY);
        if (orderService.updateWithOptimisticLocker(order) == 0) {
            return WxPayNotifyResponse.fail("Update data has expired");
        }

        //  The payment is successful, there is group purchase information, update the group purchase information
        LitemallGroupon groupon = grouponService.queryByOrderId(order.getId());
        if (groupon != null) {
            LitemallGrouponRules grouponRules = grouponRulesService.findById(groupon.getRulesId());

            //Shared images are created only if the originator
            if (groupon.getGrouponId() == 0) {
                String url = qCodeService.createGrouponShareImage(grouponRules.getGoodsName(), grouponRules.getPicUrl(), groupon);
                groupon.setShareUrl(url);
            }
            groupon.setStatus(GrouponConstant.STATUS_ON);
            if (grouponService.updateById(groupon) == 0) {
                return WxPayNotifyResponse.fail("Update data has expired");
            }


            List<LitemallGroupon> grouponList = grouponService.queryJoinRecord(groupon.getGrouponId());
            if (groupon.getGrouponId() != 0 && (grouponList.size() >= grouponRules.getDiscountMember() - 1)) {
                for (LitemallGroupon grouponActivity : grouponList) {
                    grouponActivity.setStatus(GrouponConstant.STATUS_SUCCEED);
                    grouponService.updateById(grouponActivity);
                }

                LitemallGroupon grouponSource = grouponService.queryById(groupon.getGrouponId());
                grouponSource.setStatus(GrouponConstant.STATUS_SUCCEED);
                grouponService.updateById(grouponSource);
            }
        }

        //TODO sends email and SMS notifications, asynchronous sending is used here
        // After the order payment is successful, a text message will be sent to the user and an email will be sent to the administrator.
        notifyService.notifyMail("New order notification", order.toString());
        // The WeChat SMS platform here has restrictions on parameter length, so only the last 6 digits of the order number are intercepted.
        notifyService.notifySmsTemplateSync(order.getMobile(), NotifyType.PAY_SUCCEED, new String[]{orderSn.substring(8, 14)});

        // Cancel order timeout and unpaid tasks
        taskService.removeTask(new OrderUnpaidTask(order.getId()));

        return WxPayNotifyResponse.success("Processed successfully!");
    }

    /**
     * Apply for a refund on your order
     * <p>
     * 1. Check whether the current order can be refunded;
     * 2. Set the order application refund status.
     *
     * @param userId user ID
     * @param body order information, { orderId: xxx }
     * @return Order refund operation result
     */
    public Object refund(Integer userId, String body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Integer orderId = JacksonUtil.parseInteger(body, "orderId");
        if (orderId == null) {
            return ResponseUtil.badArgument();
        }

        LitemallOrder order = orderService.findById(userId, orderId);
        if (order == null) {
            return ResponseUtil.badArgument();
        }
        if (!order.getUserId().equals(userId)) {
            return ResponseUtil.badArgumentValue();
        }

        OrderHandleOption handleOption = OrderUtil.build(order);
        if (!handleOption.isRefund()) {
            return ResponseUtil.fail(ORDER_INVALID_OPERATION, "Order cannot be canceled");
        }

        // Set order application refund status
        order.setOrderStatus(OrderUtil.STATUS_REFUND);
        if (orderService.updateWithOptimisticLocker(order) == 0) {
            return ResponseUtil.updatedDateExpired();
        }

        //TODO sends email and SMS notifications, asynchronous sending is used here
        // If a user applies for a refund, the operator will be notified by email
        notifyService.notifyMail("Refund request", order.toString());

        return ResponseUtil.ok();
    }

    /**
     * confirm the receipt of goods
     * <p>
     * 1. Check whether the current order can be confirmed to be received;
     * 2. Set the order confirmation receipt status.
     *
     * @param userId user ID
     * @param body order information, { orderId: xxx }
     * @return order operation result
     */
    public Object confirm(Integer userId, String body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Integer orderId = JacksonUtil.parseInteger(body, "orderId");
        if (orderId == null) {
            return ResponseUtil.badArgument();
        }

        LitemallOrder order = orderService.findById(userId, orderId);
        if (order == null) {
            return ResponseUtil.badArgument();
        }
        if (!order.getUserId().equals(userId)) {
            return ResponseUtil.badArgumentValue();
        }

        OrderHandleOption handleOption = OrderUtil.build(order);
        if (!handleOption.isConfirm()) {
            return ResponseUtil.fail(ORDER_INVALID_OPERATION, "Order cannot be confirmed for receipt");
        }

        Short comments = orderGoodsService.getComments(orderId);
        order.setComments(comments);

        order.setOrderStatus(OrderUtil.STATUS_CONFIRM);
        order.setConfirmTime(LocalDateTime.now());
        if (orderService.updateWithOptimisticLocker(order) == 0) {
            return ResponseUtil.updatedDateExpired();
        }
        return ResponseUtil.ok();
    }

    /**
     * Delete order
     * <p>
     * 1. Check whether the current order can be deleted;
     * 2. Delete the order.
     *
     * @param userId user ID
     * @param body order information, { orderId: xxx }
     * @return order operation result
     */
    public Object delete(Integer userId, String body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Integer orderId = JacksonUtil.parseInteger(body, "orderId");
        if (orderId == null) {
            return ResponseUtil.badArgument();
        }

        LitemallOrder order = orderService.findById(userId, orderId);
        if (order == null) {
            return ResponseUtil.badArgument();
        }
        if (!order.getUserId().equals(userId)) {
            return ResponseUtil.badArgumentValue();
        }

        OrderHandleOption handleOption = OrderUtil.build(order);
        if (!handleOption.isDelete()) {
            return ResponseUtil.fail(ORDER_INVALID_OPERATION, "Order cannot be deleted");
        }

        // Order order_status has no field to identify deletion
        // Instead, there is a special delete field indicating whether to delete
        orderService.deleteById(orderId);
        // The after-sales service will also be deleted at the same time
        aftersaleService.deleteByOrderId(userId, orderId);

        return ResponseUtil.ok();
    }

    /**
     * Product information of orders to be evaluated
     *
     * @param userId user ID
     * @param ogid order item ID
     * @return Order product information to be evaluated
     */
    public Object goods(Integer userId, Integer ogid) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        LitemallOrderGoods orderGoods = orderGoodsService.findById(ogid);

        if (orderGoods != null) {
            Integer orderId = orderGoods.getOrderId();
            LitemallOrder order = orderService.findById(orderId);
            if (!order.getUserId().equals(userId)) {
                return ResponseUtil.badArgument();
            }
        }
        return ResponseUtil.ok(orderGoods);
    }

    /**
     * Evaluate order items
     * <p>
     * You can evaluate within 7 days after confirming the receipt of the product or the system automatically confirms the receipt of the product, and cannot evaluate after the expiration date.
     *
     * @param userId user ID
     * @param body order information, { orderId: xxx }
     * @return order operation result
     */
    public Object comment(Integer userId, String body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        Integer orderGoodsId = JacksonUtil.parseInteger(body, "orderGoodsId");
        if (orderGoodsId == null) {
            return ResponseUtil.badArgument();
        }
        LitemallOrderGoods orderGoods = orderGoodsService.findById(orderGoodsId);
        if (orderGoods == null) {
            return ResponseUtil.badArgumentValue();
        }
        Integer orderId = orderGoods.getOrderId();
        LitemallOrder order = orderService.findById(userId, orderId);
        if (order == null) {
            return ResponseUtil.badArgumentValue();
        }
        if (!OrderUtil.isConfirmStatus(order) && !OrderUtil.isAutoConfirmStatus(order)) {
            return ResponseUtil.fail(ORDER_INVALID_OPERATION, "The current product cannot be evaluated");
        }
        if (!order.getUserId().equals(userId)) {
            return ResponseUtil.fail(ORDER_INVALID, "The current product does not belong to the user");
        }
        Integer commentId = orderGoods.getComment();
        if (commentId == -1) {
            return ResponseUtil.fail(ORDER_COMMENT_EXPIRED, "The current product evaluation time has expired");
        }
        if (commentId != 0) {
            return ResponseUtil.fail(ORDER_COMMENTED, "The order product has been evaluated");
        }

        String content = JacksonUtil.parseString(body, "content");
        Integer star = JacksonUtil.parseInteger(body, "star");
        if (star == null || star < 0 || star > 5) {
            return ResponseUtil.badArgumentValue();
        }
        Boolean hasPicture = JacksonUtil.parseBoolean(body, "hasPicture");
        List<String> picUrls = JacksonUtil.parseStringList(body, "picUrls");
        if (hasPicture == null || !hasPicture) {
            picUrls = new ArrayList<>(0);
        }

        // 1. Create a review
        LitemallComment comment = new LitemallComment();
        comment.setUserId(userId);
        comment.setType((byte) 0);
        comment.setValueId(orderGoods.getGoodsId());
        comment.setStar(star.shortValue());
        comment.setContent(content);
        comment.setHasPicture(hasPicture);
        comment.setPicUrls(picUrls.toArray(new String[]{}));
        commentService.save(comment);

        // 2. Update the review list of order items
        orderGoods.setComment(comment.getId());
        orderGoodsService.updateById(orderGoods);

        // 3. Update the evaluable quantity of unevaluated order items in the order
        Short commentCount = order.getComments();
        if (commentCount > 0) {
            commentCount--;
        }
        order.setComments(commentCount);
        orderService.updateWithOptimisticLocker(order);

        return ResponseUtil.ok();
    }

    /**
     *Cancel order/refund coupon
     * <br/>
     * @param orderId
     * @return void
     * @author Tyson
     * @date 2020/6/8/0008 1:41
     */
    public void releaseCoupon(Integer orderId) {
        List<LitemallCouponUser> couponUsers = couponUserService.findByOid(orderId);
        for (LitemallCouponUser couponUser: couponUsers) {
            // Coupon status is set to available
            couponUser.setStatus(CouponUserConstant.STATUS_USABLE);
            couponUser.setUpdateTime(LocalDateTime.now());
            couponUserService.update(couponUser);
        }
    }
}