package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.core.notify.NotifyService;
import org.linlinjava.litemall.core.notify.NotifyType;
import org.linlinjava.litemall.core.qcode.QCodeService;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.core.task.TaskService;
import org.linlinjava.litemall.db.domain.*;
import org.linlinjava.litemall.db.util.CouponUserConstant;
import org.linlinjava.litemall.db.util.GrouponConstant;
import org.linlinjava.litemall.order.application.LitemallIOrderService;
import org.linlinjava.litemall.order.application.util.exception.coupon.LitemallInvalidCouponException;
import org.linlinjava.litemall.order.application.util.exception.coupon.LitemallValidCouponException;
import org.linlinjava.litemall.order.application.util.exception.groupon.LitemallAlreadyJoinGrouponException;
import org.linlinjava.litemall.order.application.util.exception.groupon.LitemallCannotJoinOwnGrouponException;
import org.linlinjava.litemall.order.application.util.exception.groupon.LitemallGrouponFullException;
import org.linlinjava.litemall.order.application.util.exception.groupon.LitemallGrouponRulesNotFoundException;
import org.linlinjava.litemall.order.application.util.exception.product.LitemallInsufficientStockException;
import org.linlinjava.litemall.order.application.util.exception.product.LitemallProductNotFoundException;
import org.linlinjava.litemall.order.domain.model.agregates.*;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderSubmitResult;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.model.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.model.repositories.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallCouponUserStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallGrouponStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.service.coupon.LitemallCouponService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Service
public class LitemallOrderServiceImpl implements LitemallIOrderService {

    private final LitemallOrderRepository orderRepository;
    private final LitemallGrouponRepository grouponRepository;
    private final LitemallGrouponRulesRepository grouponRulesRepository;
    private final LitemallUserRepository userRepository;
    private final LitemallCartRepository cartRepository;
    private final LitemallCouponRepository couponRepository;
    private final LitemallGoodsProductRepository goodsProductRepository;
    private final LitemallProductRepository productRepository;
    private final LitemallAddressRepository addressRepository;
    private final LitemallOrderGoodsRepository orderGoodsRepository;

    private final LitemallDomainEventPublisher domainEventPublisher;

    // Service internal to orderService
    private final LitemallCouponService couponService;

    private final QCodeService qCodeService;
    private final NotifyService notifyService;
    private final TaskService taskService;

    public LitemallOrderServiceImpl(LitemallOrderRepository orderRepo,
                                    LitemallGrouponRepository grouponRepo,
                                    LitemallGrouponRulesRepository grouponRulesRepo,
                                    LitemallUserRepository userRepo,
                                    LitemallCartRepository cartRepo,
                                    LitemallCouponRepository couponRepo,
                                    LitemallProductRepository productRepo,
                                    LitemallAddressRepository addressRepo,
                                    LitemallOrderGoodsRepository orderGoodsRepo,
                                    LitemallGoodsProductRepository goodsProductRepository,
                                    LitemallCouponService couponService,
                                    LitemallDomainEventPublisher domainEventPublisher,
                                    QCodeService qCodeService,
                                    NotifyService notifyService,
                                    TaskService taskService) {
        this.orderRepository = orderRepo;
        this.grouponRepository = grouponRepo;
        this.grouponRulesRepository = grouponRulesRepo;
        this.userRepository = userRepo;
        this.cartRepository = cartRepo;
        this.couponRepository = couponRepo;
        this.productRepository = productRepo;
        this.addressRepository = addressRepo;
        this.orderGoodsRepository = orderGoodsRepo;
        this.goodsProductRepository = goodsProductRepository;
        this.couponService = couponService;
        this.domainEventPublisher = domainEventPublisher;
        this.qCodeService = qCodeService;
        this.notifyService = notifyService;
        this.taskService = taskService;
    }


    @Override
    public LitemallOrderSubmitResult placeOrder(LitemallPlaceOrderCommand command) {

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


        LitemallUser user = userRepository.findById(userId);

        if(user != null){
            throw new IllegalArgumentException("User is not found");
        }

        // Validate and process Groupon if applicable
        LitemallGrouponRulesAggregate grouponRulesAggregate = validateAndGetGroupon(userId, command.getGrouponRulesId(), command.getGrouponLinkId());
        // Get the shipping add
        LitemallAddressAggregate addressAggregate = addressRepository.findAddress(userId, addressId);
        // Get the cartItems
        List<LitemallCartAggregate> checkedItems = getCheckedCartItems(new LitemallCartId(command.getCartId()), userId);



        // Validate the productStock
        validateProductStock(checkedItems);

        // Check groupon discount
        BigDecimal grouponPrice = new BigDecimal(0);
        LitemallGrouponRulesAggregate rulesAggregate = grouponRulesRepository.findById(grouponRulesId);
        if(rulesAggregate != null){
            grouponPrice = rulesAggregate.getDiscount();
        }

        // Get the checkedItem list
        List<LitemallCartAggregate> checkedGoodsList = null;
        if (command.getCartId().equals(0)) {
            checkedGoodsList = cartRepository.findCheckedByUserId(userId);
        } else {
            LitemallCartAggregate cart = cartRepository.findById(new LitemallCartId(command.getCartId()));
            checkedGoodsList = new ArrayList<>(1);
            checkedGoodsList.add(cart);
        }
        if (checkedGoodsList.isEmpty()) {
            throw new IllegalArgumentException("The Checked goods list has not to be empty");
        }

        // Calculate checked goods price
        BigDecimal checkedGoodsPrice;

        checkedGoodsPrice = priceCalculation(checkedGoodsList, grouponRulesAggregate, grouponPrice);
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
        orderAggregateRoot.setOrderSn(orderRepository.generateOrderSn(userId));

        //orderAggregateRoot.setOrderStatus(OrderUtil.STATUS_CREATE);
        orderAggregateRoot.setOrderStatus(LitemallOrderStatus.CREATED);
        orderAggregateRoot.setConsignee(addressAggregate.getName());
        orderAggregateRoot.setMobile(addressAggregate.getTel());
        orderAggregateRoot.setMessage(command.getMessage());
        String detailedAddress = addressAggregate.getProvince() + addressAggregate.getCity() + addressAggregate.getCounty() + " " + addressAggregate.getAddressDetail();
        orderAggregateRoot.setAddress(detailedAddress);

        orderAggregateRoot.setGoodsPrice(new LitemallMoney(checkedGoodsPrice));
        orderAggregateRoot.setFreightPrice(new LitemallMoney(freightPrice));
        orderAggregateRoot.setCouponPrice(new LitemallMoney(couponPrice));
        orderAggregateRoot.setIntegralPrice(new LitemallMoney(integralPrice));
        orderAggregateRoot.setOrderPrice(new LitemallMoney(orderTotalPrice));
        orderAggregateRoot.setActualPrice(new LitemallMoney(actualPrice));

        if(grouponRulesAggregate != null){
            orderAggregateRoot.setGrouponPrice(new LitemallMoney(grouponPrice));
        }else {
            orderAggregateRoot.setGrouponPrice(new LitemallMoney(new BigDecimal(0)));
        }
        // Save the order
        orderRepository.addOrder(orderAggregateRoot);

        // Get the id from the order
        LitemallOrderId orderId1 = orderRepository.findById(orderAggregateRoot.getOrderId()).getOrderId();

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
            LitemallGoodsProductId productId = checkGoods.getProductId();
            LitemallGoodsProductAggregate product = goodsProductRepository.findById(productId).get();

            int remainNumber = product.getNumber() - checkGoods.getNumber();
            if (remainNumber < 0) {
                throw new RuntimeException("The quantity of the ordered product is greater than the inventory");
            }
            if (goodsProductRepository.reduceStock(productId, checkGoods.getNumber().shortValue()) == 0) {
                throw new RuntimeException("Product inventory reduction failed");
            }
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
        if(orderAggregateRoot.getActualPrice().getAmount().equals(new BigDecimal("0.0"))){
            payed = true;

            LitemallOrderAggregateRoot newOrderAggr = new LitemallOrderAggregateRoot();
            newOrderAggr.setOrderId(orderId1);
            newOrderAggr.setOrderStatus(LitemallOrderStatus.PAID);

            orderRepository.updateSelective(newOrderAggr);

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
            notifyService.notifySmsTemplateSync(orderAggregateRoot.getMobile(), NotifyType.PAY_SUCCEED, new String[]{orderAggregateRoot.getOrderSn().substring(8, 14)});

        } else {
            // Order payment overdue task
            taskService.addTask(new OrderUnpaidTask(orderId));
        }

        //publish domain events

        //Validate and process groupon if available
        return new LitemallOrderSubmitResult();
    }

    /**
     *
     * @param grouponRulesId
     * @param grouponLinkId
     * @param userId
     * @return
     */
    private LitemallGrouponRulesAggregate validateAndGetGroupon(LitemallUserId userId, Integer grouponRulesId, Integer grouponLinkId) {

        if(grouponRulesId == null || grouponRulesId <= 0){
            return null;
        }

        LitemallGrouponRulesAggregate grouponRules = grouponRulesRepository.findById(new LitemallGrouponRulesId(grouponRulesId));
        if(grouponRules == null){
            throw new LitemallGrouponRulesNotFoundException("Groupon rules not found.");
        }

        if(grouponLinkId != null && grouponLinkId > 0){
            LitemallGrouponId linkId = new LitemallGrouponId(grouponLinkId);

            if(grouponRepository.countByGrouponId(linkId) >= (grouponRules.getDiscountMember() - 1)){
                throw new LitemallGrouponFullException();
            }

            if(grouponRepository.existsByUserIdOrGrouponId(userId, linkId)){
                throw new LitemallAlreadyJoinGrouponException();
            }

            LitemallGrouponAggregate groupon = grouponRepository.findById(linkId);

            if(groupon.getCreatorUserId().getId().equals(userId.getId())){
                throw new LitemallCannotJoinOwnGrouponException("You cannot join your own groupon.");
            }
        }
      return grouponRules;
    }

    /**
     *
     * @param cartId
     * @param userId
     * @return
     */
    private List<LitemallCartAggregate> getCheckedCartItems(LitemallCartId cartId, LitemallUserId userId){

        if(cartId.getId() == 0){
            return cartRepository.findCheckedByUserId(userId);
        } else {
            List<LitemallCartAggregate> checkedCartItems = new ArrayList<>(0);
            LitemallCartAggregate checkedItems  = cartRepository.findById(cartId);
            checkedCartItems.add(checkedItems);
            return checkedCartItems;
        }
    }


    /**
     *
     * @param checkedCartItems
     */
    private void validateProductStock(List<LitemallCartAggregate> checkedCartItems){
        for(LitemallCartAggregate cartItem : checkedCartItems){

            LitemallGoodsProduct goodsProduct = productRepository.findById(cartItem.getProductId());
            if(goodsProduct == null){
                throw new LitemallProductNotFoundException("Product not found.");
            }

            if(goodsProduct.getNumber() < cartItem.getNumber()){
                throw new LitemallInsufficientStockException(cartItem.getProductId());
            }
        }
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
     * @param checkedCartItems
     */
    private void reduceProductStock(List<LitemallCartAggregate> checkedCartItems){
        for(LitemallCartAggregate cartItem : checkedCartItems){
            if(cartItem != null){
                productRepository.reduceStock(cartItem.getProductId(), cartItem.getNumber().shortValue());
            }
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
            grouponAggregate = LitemallGrouponAggregate.createJoin(orderAggregateRoot.getOrderId(), orderAggregateRoot.getUserId(), rulesAggregate.getGrouponRulesId(), baseGroupon);

        } else {
            grouponAggregate = LitemallGrouponAggregate.createNewGroupon(orderAggregateRoot.getOrderId(), orderAggregateRoot.getUserId(), rulesAggregate.getGrouponRulesId());
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

    public BigDecimal priceCalculation(List<LitemallCartAggregate> checkedGoodsList, LitemallGrouponRulesAggregate grouponRules, BigDecimal grouponPrice){
        BigDecimal checkedGoodsPrice = new BigDecimal(0);
        for (LitemallCartAggregate checkedGoods : checkedGoodsList) {
            //  Only when the product ID meets the group purchase specifications will the group purchase discount be available
            if (grouponRules != null && grouponRules.getGoodsId().equals(checkedGoods.getGoodsId())) {
                checkedGoodsPrice = checkedGoodsPrice.add(checkedGoods.getPrice().getAmount().subtract(grouponPrice).multiply(new BigDecimal(checkedGoods.getNumber())));
            } else {
                checkedGoodsPrice = checkedGoodsPrice.add(checkedGoods.getPrice().getAmount().multiply(new BigDecimal(checkedGoods.getNumber())));
            }
        }
        return checkedGoodsPrice;
    }

    public void updateGrouponAfterPayment(LitemallGrouponAggregate grouponAggregate, LitemallGrouponRulesAggregate grouponRulesAggregate){

            //Shared images are created only if the originator
            if (grouponAggregate.getGrouponId().getId() == 0) {
                LitemallGroupon groupon = grouponRepository.convertToDataModel(grouponAggregate)
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


}
