package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.core.notify.NotifyService;
import org.linlinjava.litemall.core.qcode.QCodeService;
import org.linlinjava.litemall.core.task.TaskService;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.db.domain.LitemallGroupon;
import org.linlinjava.litemall.db.domain.LitemallGrouponRules;
import org.linlinjava.litemall.db.domain.LitemallUser;
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
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.service.coupon.LitemallCouponService;
import org.springframework.stereotype.Service;

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
    private final LitemallProductRepository productRepository;
    private final LitemallAddressRepository addressRepository;
    private final LitemallCouponService couponService;

    private final LitemallDomainEventPublisher domainEventPublisher;
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

        LitemallUserId userId = new LitemallUserId(command.getUserId());
        LitemallUser user = userRepository.findById(userId);


        // Validate and process Groupon if applicable

        // Get the shipping add

        // Get the cartItems

        // Validate the productStock

        // Get and validate the coupon

        // Create the order
        
        // Save the order

        // Clear the cart

        // Reduce the product stock

        // Update coupon usage if applicable


        // Handle coupon create if applicable


        // Handle payment


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
    private LitemallGrouponRulesAggregate validateAndGetGroupon(Integer grouponRulesId, Integer grouponLinkId, LitemallUserId userId) {

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


}
