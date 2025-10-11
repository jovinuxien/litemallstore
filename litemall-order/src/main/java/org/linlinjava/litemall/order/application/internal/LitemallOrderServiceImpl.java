package org.linlinjava.litemall.order.application.internal;

import com.google.protobuf.ServiceException;
import org.linlinjava.litemall.core.notify.NotifyService;
import org.linlinjava.litemall.core.notify.NotifyType;
import org.linlinjava.litemall.core.qcode.QCodeService;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.core.task.TaskService;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.*;
import org.linlinjava.litemall.order.application.LitemallIOrderService;
import org.linlinjava.litemall.order.application.util.exception.coupon.LitemallInvalidCouponException;
import org.linlinjava.litemall.order.application.util.exception.coupon.LitemallValidCouponException;
import org.linlinjava.litemall.order.domain.model.agregates.*;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderSubmitResult;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.model.domainservices.groupon.LitemallGrouponValidationResult;
import org.linlinjava.litemall.order.domain.model.domainservices.order.LitemallOrderDomainService;
import org.linlinjava.litemall.order.domain.model.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.model.repositories.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons.LitemallCouponUserStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallGrouponStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.FeignResponseHandler;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.GoodsServiceFeignClient;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.UserServiceFeignClient;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.ReduceStockRequest;
import org.linlinjava.litemall.wx.task.OrderUnpaidTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


@Service
public class LitemallOrderServiceImpl implements LitemallIOrderService {

    private final LitemallOrderRepository orderRepository;
    private final LitemallGrouponRepository grouponRepository;
    private final LitemallCartRepository cartRepository;
    private final LitemallCouponRepository couponRepository;
    private final LitemallAddressRepository addressRepository;
    private final LitemallOrderGoodsRepository orderGoodsRepository;

    private final LitemallDomainEventPublisher domainEventPublisher;

    // Service internal to orderService
    private final LitemallCouponServiceLayer couponService;


    @Autowired
    private LitemallOrderDomainService orderDomainService;
    @Autowired
    private LitemallCartServiceLayer cartServiceLayer;
    @Autowired
    private LitemallGrouponServiceLayer grouponServiceLayer;
    @Autowired
    private  QCodeService qCodeService;
    @Autowired
    private  NotifyService notifyService;
    @Autowired
    private  TaskService taskService;

    @Autowired
    private GoodsServiceFeignClient goodsServiceFeignClient;
    @Autowired
    private UserServiceFeignClient userServiceFeignClient;

    public LitemallOrderServiceImpl(LitemallOrderRepository orderRepo,
                                    LitemallGrouponRepository grouponRepo,
                                    LitemallCartRepository cartRepo,
                                    LitemallCouponRepository couponRepo,
                                    LitemallAddressRepository addressRepo,
                                    LitemallOrderGoodsRepository orderGoodsRepo,
                                    LitemallCouponServiceLayer couponService,
                                    LitemallDomainEventPublisher domainEventPublisher) {
        this.orderRepository = orderRepo;
        this.grouponRepository = grouponRepo;
        this.cartRepository = cartRepo;
        this.couponRepository = couponRepo;
        this.addressRepository = addressRepo;
        this.orderGoodsRepository = orderGoodsRepo;
        this.couponService = couponService;
        this.domainEventPublisher = domainEventPublisher;
    }


    @Override
    //public LitemallOrderSubmitResult placeOrder(LitemallPlaceOrderCommand command)  {
    public Object placeOrder(LitemallPlaceOrderCommand command) throws ServiceException {

        // Validate the command
        if(command.getUserId() == null){
            throw new IllegalArgumentException("User id is required.");
        }

        if(command.getAddressId() == null){
            throw new IllegalArgumentException("Address info is required");
        }

        if(command.getGrouponRulesId() == null){
            throw  new IllegalArgumentException("The groupon is required");
        }
        if(command.getGrouponLinkId() == null){
            throw new IllegalArgumentException("The groupon link is required");
        }

        LitemallUserId userId = new LitemallUserId(command.getUserId());
        LitemallAddressId addressId = new LitemallAddressId(command.getAddressId());
        LitemallGrouponRulesId grouponRulesId = new LitemallGrouponRulesId(command.getGrouponRulesId());


       /* LitemallUserAggregate user = FeignResponseHandler.handleResponse(userServiceFeignClient.geUserById(userId.getId()), "Get userAggregate by Id");

        if(user == null){
            throw new IllegalArgumentException("User is not found");
        }*/

        // Validate and process Groupon if applicable
        LitemallGrouponValidationResult grouponValidationResult = grouponServiceLayer.validateGrouponRules(command.getUserId(), command.getGrouponRulesId(), command.getGrouponLinkId());

        // Get and Check the shipping address
        LitemallAddressAggregate addressAggregate = addressRepository.findAddress(userId, addressId);


        // Get the Checked cart items
        List<LitemallCartAggregate> checkedGoodsList = null;
        checkedGoodsList = cartServiceLayer.getCheckedCartItems(new LitemallCartId(command.getCartId()), userId);
        if(checkedGoodsList == null){
            return ResponseUtil.badArgumentValue();
        }


        // Validate the productStock
        this.orderDomainService.validateProductStock(checkedGoodsList, goodsServiceFeignClient);


        // Group purchase discount
        BigDecimal grouponPrice = new BigDecimal(0);  // initialize grouponPrice is not redundant;
        if(grouponValidationResult.isValid()) {
            grouponPrice = grouponServiceLayer.getGrouponDiscount(grouponRulesId);
        }

        // Calculate checked goods price
        BigDecimal checkedGoodsPrice;  // initialize checkedGoodsPrice is redundant;
        LitemallGrouponRulesAggregate grouponRulesAggregate = grouponServiceLayer.getGrouponRulesAggregate(grouponRulesId);
        checkedGoodsPrice = this.orderDomainService.priceCalculation(checkedGoodsList, grouponRulesAggregate, new LitemallMoney(grouponPrice));


        LitemallMoney checkedGoodsPriceMoney = new LitemallMoney(checkedGoodsPrice);
        //Calculate and get the Coupon price info
        // Amount reduced using coupons
        BigDecimal couponPrice = new BigDecimal(0);
        if(command.getCouponId() != 0 && command.getCouponId() != -1){
            LitemallCouponAggregate couponAggregate = getAndValidateCoupon(userId, new LitemallCouponId(command.getCouponId()), new LitemallCouponUserId(command.getUserCouponId()),  checkedGoodsPriceMoney,  checkedItems);

            if(couponAggregate == null){
                throw new IllegalArgumentException("The Coupon Aggregate is null");
            }
            couponPrice = couponAggregate.getDiscount();
        }

        // Calculate shipping costs based on total order price，
        // Meet the conditions (e.g. $88), free shipping，Otherwise you need to pay shipping fee（For example, $8）；
        BigDecimal freightPrice = new BigDecimal(0);
        if (checkedGoodsPrice.compareTo(SystemConfig.getFreightLimit()) < 0) {
            freightPrice = SystemConfig.getFreight();
        }
        // Other money available，For example, user points
        BigDecimal integralPrice = new BigDecimal(0);

        // Order Fee calculation: Adding freightPrice, subtracting couponPrice
        BigDecimal orderTotalPrice = checkedGoodsPrice.add(freightPrice).subtract(couponPrice).max(new BigDecimal(0));

        // Final payment: removing integralPrice from orderTotalPrice
        BigDecimal actualPrice = orderTotalPrice.subtract(integralPrice);

        LitemallOrderId orderId = null;
        LitemallOrderAggregateRoot orderAggregateRoot = new LitemallOrderAggregateRoot();

        // Create the order
        //orderId = new LitemallOrderId(0);// the OrderId to be generated
        //orderAggregateRoot.setOrderId(orderId);
        orderAggregateRoot.getOrderAggregate().setOrderSn(orderRepository.generateOrderSn(userId));

        //orderAggregateRoot.setOrderStatus(OrderUtil.STATUS_CREATE);
        orderAggregateRoot.getOrderAggregate().setOrderStatus(LitemallOrderStatus.CREATED);
        orderAggregateRoot.getOrderAggregate().setConsignee(addressAggregate.getName());
        orderAggregateRoot.getOrderAggregate().setMobile(addressAggregate.getTel());
        orderAggregateRoot.getOrderAggregate().setMessage(command.getMessage());
        String detailedAddress = addressAggregate.getProvince() + addressAggregate.getCity() + addressAggregate.getCounty() + " " + addressAggregate.getAddressDetail();
        orderAggregateRoot.getOrderAggregate().setAddress(detailedAddress);

        orderAggregateRoot.getOrderAggregate().setGoodsPrice(new LitemallMoney(checkedGoodsPrice));
        orderAggregateRoot.getOrderAggregate().setFreightPrice(new LitemallMoney(freightPrice));
        orderAggregateRoot.getOrderAggregate().setCouponPrice(new LitemallMoney(couponPrice));
        orderAggregateRoot.getOrderAggregate().setIntegralPrice(new LitemallMoney(integralPrice));
        orderAggregateRoot.getOrderAggregate().setOrderPrice(new LitemallMoney(orderTotalPrice));
        orderAggregateRoot.getOrderAggregate().setActualPrice(new LitemallMoney(actualPrice));

        if(grouponRulesAggregate != null){
            orderAggregateRoot.getOrderAggregate().setGrouponPrice(new LitemallMoney(grouponPrice));
        }else {
            orderAggregateRoot.getOrderAggregate().setGrouponPrice(new LitemallMoney(new BigDecimal(0)));
        }
        // Save the order
        orderRepository.addOrder(orderAggregateRoot);

        // Get the id from the order
        LitemallOrderId orderId1 = orderRepository.findById(orderAggregateRoot.getOrderAggregate().getOrderId()).getOrderId();

        // Add order from items
        for(LitemallCartAggregate cartGoods: checkedGoodsList){

            LitemallOrderGoodsAggregate orderGoodsAggregate = new LitemallOrderGoodsAggregate();
            orderGoodsAggregate.setOrderId(orderId1);
            orderGoodsAggregate.setGoodsId(cartGoods.getGoodsId());
            orderGoodsAggregate.setProductId(cartGoods.getProductId());

            orderGoodsAggregate.setGoodsSn(cartGoods.getGoodsSn());
            orderGoodsAggregate.setGoodsName(cartGoods.getGoodsName());

            orderGoodsAggregate.setPrice(cartGoods.getPrice());
            orderGoodsAggregate.setNumber(cartGoods.getNumber().shortValue());
            orderGoodsAggregate.setSpecifications(cartGoods.getSpecifications());

            orderGoodsAggregate.setAddTime(LocalDateTime.now());

            orderGoodsRepository.add(orderGoodsAggregate);
        }
        // Clear the cart
        // Delete product information in shopping cart
        clearCart(userId, new LitemallCartId(command.getCartId()));
        // Reduce the product stock
        for (LitemallCartAggregate checkGoods : checkedGoodsList) {
            //Here the idea is to get goods product info from Goods microservice
            // and fetch the corresponding product information
            LitemallGoodsAggregate goodsAggregate = FeignResponseHandler.handleResponse(goodsServiceFeignClient.getGoodsAggregate(checkGoods.getGoodsId().getId()), "Get Goods Operation");
            LitemallGoodsProductAggregate product = FeignResponseHandler.handleResponse(goodsServiceFeignClient.getGoodsProductAggregate(checkGoods.getProductId().getId()), "Get Goods Product Operation");

            LitemallGoodsProductId productId = checkGoods.getProductId();
            //LitemallGoodsProductAggregate product = goodsProductRepository.findById(productId).get();

            int remainNumber = product.getNumber() - checkGoods.getNumber();
            if (remainNumber < 0) {
                throw new RuntimeException("The quantity of the ordered product is greater than the inventory");
            }
            var reduceStockRequest = new ReduceStockRequest(productId.getId(), checkGoods.getNumber());
            FeignResponseHandler.handleResponse(goodsServiceFeignClient.reduceStock(reduceStockRequest), "reduce Stock Operation");
            /*if (goodsProductRepository.reduceStock(productId, checkGoods.getNumber().shortValue()) == 0) {
                throw new RuntimeException("Product inventory reduction failed");
            }*/
        }
        // Update coupon usage if applicable
        if (command.getCouponId() != 0 && command.getCouponId() != -1) {
            LitemallCouponUserAggregate couponUserAggregate = couponService.getUserCouponById(new LitemallCouponUserId(command.getUserCouponId()));
            //couponUserAggregate.setStatus(CouponUserConstant.STATUS_USED);
            couponUserAggregate.setStatus(LitemallCouponUserStatus.USED);
            couponUserAggregate.setUsedTime(LocalDateTime.now());
            couponUserAggregate.setOrderId(orderId);


            couponService.updateCouponUser(couponUserAggregate);
        }

        //If it's a groupon purchase project, add group buying information
        if(command.getGrouponRulesId() != null && command.getGrouponLinkId() > 0){

            LitemallGrouponAggregate grouponAggregate = new LitemallGrouponAggregate();
            grouponAggregate.setOrderId(orderId1);
            grouponAggregate.setGrouponStatus(LitemallGrouponStatus.STATUS_NONE);
            grouponAggregate.setUserId(userId);
            grouponAggregate.setGrouponRulesId(grouponRulesId);

            Integer grouponLinkId = command.getGrouponLinkId();

            //participants
            if (grouponLinkId != null && grouponLinkId > 0) {

                //Participated group buying records
                LitemallGrouponAggregate baseGrouponAggregate = grouponRepository.findById(new LitemallGrouponId(command.getGrouponLinkId()));
                grouponAggregate.setCreatorUserId(baseGrouponAggregate.getCreatorUserId());
                grouponAggregate.setGrouponId(new LitemallGrouponId(command.getGrouponLinkId()));
                grouponAggregate.setShareUrl(baseGrouponAggregate.getShareUrl());

                grouponRepository.saveGroupon(grouponAggregate);
            } else {
                grouponAggregate.setCreatorUserId(userId);
                grouponAggregate.setCreatorUserTime(LocalDateTime.now());
                grouponAggregate.setGrouponId(new LitemallGrouponId(0));
                grouponRepository.saveGroupon(grouponAggregate);
                grouponLinkId = grouponAggregate.getGrouponId().getId();
            }
        }
        // Handle coupon create if applicable

        // Handle payment

        // NOTE: It is recommended that developers verify the following code from the business scenario，
        //              Prevent users from using business bugs to make orders skip the payment process
        // If the actual payment fee for the order is 0，
        //      Then directly skip the payment and become the status of pending shipment.

        boolean payed = false;
        if(orderAggregateRoot.getOrderAggregate().getActualPrice().getAmount().equals(new BigDecimal("0.0"))){
            payed = true;

            LitemallOrderAggregateRoot newOrderAggr = new LitemallOrderAggregateRoot();
            newOrderAggr.getOrderAggregate().setOrderId(orderId1);
            newOrderAggr.getOrderAggregate().setOrderStatus(LitemallOrderStatus.PAID);

            orderRepository.updateSelective(newOrderAggr.getOrderAggregate());

            //  Payment successful，There is group buying information，Update group buying information
            LitemallGrouponAggregate grouponAggregate = grouponRepository.getGrouponByOrderId(orderId1);

            if(grouponAggregate != null){
                grouponRulesAggregate = grouponRulesRepository.findById(grouponAggregate.getGrouponRulesId());
                updateGrouponAfterPayment(grouponAggregate, grouponRulesAggregate);
            }

            //TODO Send email and SMS notifications，Asynchronous sending is used here
            //          After the order payment is successful，
            //          A text message will be sent to the user，and send an email to the administrator
            notifyService.notifyMail("New order notification", orderAggregateRoot.toString());
            // Here, WeChat’s SMS platform has restrictions on parameter length，
            //      Therefore, only the last 6 digits of the order number are truncated.
            notifyService.notifySmsTemplateSync(orderAggregateRoot.getOrderAggregate().getMobile(), NotifyType.PAY_SUCCEED, new String[]{orderAggregateRoot.getOrderAggregate().getOrderSn().substring(8, 14)});

        } else {
            // Order payment overdue task
            taskService.addTask(new OrderUnpaidTask(orderId1.getId()));
        }

        //publish domain events

        //Validate and process groupon if available
        return new LitemallOrderSubmitResult(
                orderId1.getId(),
               !payed,
                command.getGrouponLinkId()
        );
    }





    /**
     *
     * @param userId
     * @param couponId
     * @param couponUserId
     * @param checkedGoodsPrice
     * @param checkedCartItems
     * @return
     */
    private LitemallCouponAggregate getAndValidateCoupon(LitemallUserId userId, LitemallCouponId couponId,
                                                         LitemallCouponUserId couponUserId, LitemallMoney checkedGoodsPrice,
                                                         List<LitemallCartAggregate> checkedCartItems) {

        if (couponId.getId() == null || couponId.getId() == 0 || couponId.getId() == -1) {
            return null;
        }
        LitemallCouponAggregate checkedCoupon = couponService.checkCoupon(userId, couponId, couponUserId, checkedGoodsPrice,  checkedCartItems);

        if(checkedCoupon == null){
            throw new LitemallInvalidCouponException("Coupon not found.");
        }
        return checkedCoupon;
    }

    /**
     *
     * @param userId
     * @param cartId
     */
    private void clearCart(LitemallUserId userId, LitemallCartId cartId){
        if(cartId.getId() == 0){
            cartRepository.clearCheckedByUserId(userId);
        }else {
            cartRepository.deleteById(cartId);
        }
    }
    /**
     *
     * @param command
     * @param orderId
     */
    private void updateCouponUsage(LitemallPlaceOrderCommand command, LitemallOrderId orderId){
        if(command.getCouponId() != null && command.getCouponId() > 0){
            couponService.couponUserUpdateUsage(new LitemallCouponUserId(command.getUserCouponId()), orderId);
        } else {
            throw new LitemallValidCouponException("The coupon is still valid");
        }
    }


    public LitemallGrouponAggregate handleGrouponCreation(LitemallPlaceOrderCommand command, LitemallOrderAggregateRoot orderAggregateRoot, LitemallGrouponRulesAggregate rulesAggregate) {

        if (rulesAggregate == null) {
            return null;
        }
        LitemallGrouponAggregate grouponAggregate;
        LitemallGrouponAggregate baseGroupon = null;
        if (command.getGrouponRulesId() != null && command.getGrouponLinkId() > 0) {
            baseGroupon = grouponRepository.findById(new LitemallGrouponId(command.getGrouponLinkId()));
            grouponAggregate = LitemallGrouponAggregate.createJoin(orderAggregateRoot.getOrderAggregate().getOrderId(), orderAggregateRoot.getOrderAggregate().getUserId(), rulesAggregate.getGrouponRulesId(), baseGroupon);

        } else {
            grouponAggregate = LitemallGrouponAggregate.createNewGroupon(orderAggregateRoot.getOrderAggregate().getOrderId(), orderAggregateRoot.getOrderAggregate().getUserId(), rulesAggregate.getGrouponRulesId());
        }

        grouponRepository.saveGroupon(grouponAggregate);
        return grouponAggregate;

    }

    public void publishDomainEvents(LitemallOrderAggregateRoot orderAggregateRoot, LitemallGrouponAggregate grouponAggregate){
        orderAggregateRoot.getDomainEvents().forEach(this.domainEventPublisher::publish);

        if(grouponAggregate != null){
            grouponAggregate.getDomainEvents().forEach(this.domainEventPublisher::publish);
        }
    }



    public void updateGrouponAfterPayment(LitemallGrouponAggregate grouponAggregate, LitemallGrouponRulesAggregate grouponRulesAggregate){

            //Shared images are created only if the originator
            if (grouponAggregate.getGrouponId().getId() == 0) {
                LitemallGroupon groupon = grouponRepository.convertToDataModel(grouponAggregate);
                String url = qCodeService.createGrouponShareImage(grouponRulesAggregate.getGoodsName(), grouponRulesAggregate.getPicUrl(), groupon);
                groupon.setShareUrl(url);
            }
            //grouponAggregate.setGrouponStatus(GrouponConstant.STATUS_ON);
            grouponAggregate.setGrouponStatus(LitemallGrouponStatus.STATUS_ON);
            if (grouponRepository.updateById(grouponAggregate) == 0) {
                throw new RuntimeException("Update data has expired");
            }

            List<LitemallGrouponAggregate> grouponList = grouponRepository.getJoinRecord(grouponAggregate.getGrouponId());
            if (grouponAggregate.getGrouponId().getId() != 0 && (grouponList.size() >= grouponRulesAggregate.getDiscountMember() - 1)) {
                for (LitemallGrouponAggregate grouponActivity : grouponList) {
                    //grouponActivity.setGrouponStatus(GrouponConstant.STATUS_SUCCEED);
                    grouponActivity.setGrouponStatus(LitemallGrouponStatus.STATUS_SUCCEED);
                    grouponRepository.updateById(grouponActivity);
                }

                LitemallGrouponAggregate grouponSource = grouponRepository.findById(grouponAggregate.getGrouponId());
                grouponSource.setGrouponStatus(LitemallGrouponStatus.STATUS_SUCCEED);
                grouponRepository.updateById(grouponSource);
            }
    }


    public LitemallOrderAggregate getOrderAggregate(LitemallOrderId orderId) {
        return orderRepository.findById(orderId);
    }

    public void cancelOrder(LitemallOrderId orderId, String reason) {
        LitemallOrderAggregate orderAggregate =  orderRepository.findById(orderId);

        if(orderAggregate == null){
            //throw new LitemallOrderNotFoundException("Order not found");
            throw new IllegalArgumentException("Order not found");
        }
        orderAggregate.cancel(reason);
        // 4. Publish a domain event to notify other parts of the system: Moved to LitemallOrderServiceImpl class.
        // The steps 1,2 and 3 of this cancel method are implemented in LitemallOrderAggregate class
        List<LitemallDomainEvent> domainEvents = orderAggregate.getDomainEvents();
        // The logic of publishing domain events is moved to LitemallDomainEventPublisher class
    }


}
