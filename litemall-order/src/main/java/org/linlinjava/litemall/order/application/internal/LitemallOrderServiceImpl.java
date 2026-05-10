package org.linlinjava.litemall.order.application.internal;

import com.google.protobuf.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.core.notify.NotifyService;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.core.task.TaskService;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.order.application.LitemallIOrderService;
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
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponValidationResult;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons.LitemallCouponUserStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.AggregatesValidationContext;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.FeignResponseHandler;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.GoodsServiceFeignClient;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.UserServiceFeignClient;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.BatchGoodsRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.BatchProductsRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.ReduceStockRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


@Service
@Slf4j
public class LitemallOrderServiceImpl implements LitemallIOrderService {

    private final LitemallOrderRepository orderRepository;
    private final LitemallCartRepository cartRepository;
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
                                    LitemallAddressRepository addressRepo,
                                    LitemallOrderGoodsRepository orderGoodsRepo,
                                    LitemallCouponServiceLayer couponService,
                                    LitemallDomainEventPublisher domainEventPublisher) {
        this.orderRepository = orderRepo;
        this.cartRepository = cartRepo;
        this.addressRepository = addressRepo;
        this.orderGoodsRepository = orderGoodsRepo;
        this.couponService = couponService;
        this.domainEventPublisher = domainEventPublisher;
    }


    @Override
    //public LitemallOrderSubmitResult placeOrder(LitemallPlaceOrderCommand command)  {
    public LitemallOrderSubmitResult placeOrder(LitemallPlaceOrderCommand command) throws ServiceException {

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
        if(command.getUserCouponId() == null){
            throw new IllegalArgumentException("The user coupon is required");
        }
        if(command.getCouponId() == null){
            throw  new IllegalArgumentException("The coupon is required");
        }
        if(command.getGrouponLinkId() == null){
            throw new IllegalArgumentException("The groupon link is required");
        }

        LitemallUserId cmdUserId = new LitemallUserId(command.getUserId());
        LitemallAddressId cmdAddressId = new LitemallAddressId(command.getAddressId());
        LitemallGrouponRulesId cmdGrouponRulesId = new LitemallGrouponRulesId(command.getGrouponRulesId());
        LitemallCouponId cmdCouponId =  new LitemallCouponId(command.getCouponId());
        LitemallCouponUserId cmdCouponUserId = new LitemallCouponUserId(command.getUserCouponId());


       /* LitemallUserAggregate user = FeignResponseHandler.handleResponse(userServiceFeignClient.geUserById(userId.getId()), "Get userAggregate by Id");

        if(user == null){
            throw new IllegalArgumentException("User is not found");
        }*/

        // Validate and process Groupon if applicable
        LitemallGrouponValidationResult grouponValidationResult = grouponServiceLayer.validateGrouponRules(cmdUserId.getId(), cmdGrouponRulesId.getId(), command.getGrouponLinkId());

        // Get and Check the shipping address
        LitemallAddressAggregate addressAggregate = addressRepository.findAddress(cmdUserId, cmdAddressId);


        // Get the Checked cart items
        List<LitemallCartAggregate> cartList = null;
        cartList = cartServiceLayer.getCheckedCartItems(new LitemallCartId(command.getCartId()), cmdUserId);

        if(cartList == null){
            return LitemallOrderSubmitResult.failed();
        }

        // Validate the productStock
        this.orderDomainService.validateProductStock(cartList, goodsServiceFeignClient);

        // Group purchase discount
        BigDecimal grouponPrice = new BigDecimal(0);  // initialize grouponPrice is not redundant;
        if(grouponValidationResult.isValid()) {
            grouponPrice = grouponServiceLayer.getGrouponDiscount(cmdGrouponRulesId).getAmount();
        }

        // Calculate checked goods price
        BigDecimal checkedGoodsPrice;  // initialize checkedGoodsPrice is redundant;
        LitemallGrouponRulesAggregate grouponRulesAggregate = grouponServiceLayer.getGrouponRulesAggregate(cmdGrouponRulesId);
        checkedGoodsPrice = this.orderDomainService.priceCalculation(cartList, grouponRulesAggregate, new LitemallMoney(grouponPrice));


        //LitemallMoney checkedGoodsPriceMoney = new LitemallMoney(checkedGoodsPrice);
        //Calculate and get the Coupon price info
        // Amount reduced using coupons
        BigDecimal couponPrice = new BigDecimal(0);
        if(cmdCouponId.getId() != 0 && cmdCouponId.getId() != -1){
            LitemallCouponValidationResult couponValidationResult = couponService.validateCouponApplication(cmdUserId, cmdCouponId, cartList);
            if(!couponValidationResult.isValid()){
                ResponseUtil.badArgumentType(couponValidationResult.getMessage());
            }

            LitemallCouponAggregate couponAggregate = couponService.getCouponAggregate(cmdCouponId);
            if(checkedGoodsPrice.compareTo(couponAggregate.getMinPrice()) < 0) {
                return null;
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

        LitemallOrderId newOrderId = null;
        LitemallOrderAggregate orderAggregate =  new LitemallOrderAggregate();

        // Order creation
        newOrderId = new LitemallOrderId(0);// the OrderId to be generated
        orderAggregate.setOrderId(newOrderId);
        orderAggregate.setOrderSn(orderRepository.generateOrderSn(cmdUserId));

        orderAggregate.setOrderStatus(LitemallOrderStatus.CREATED);
        orderAggregate.setConsignee(addressAggregate.getName());
        orderAggregate.setMobile(addressAggregate.getTel());
        orderAggregate.setMessage(command.getMessage());
        String detailedAddress = addressAggregate.getProvince() + addressAggregate.getCity() + addressAggregate.getCounty() + " " + addressAggregate.getAddressDetail();
        orderAggregate.setAddress(detailedAddress);
        orderAggregate.setGoodsPrice(new LitemallMoney(checkedGoodsPrice));
        orderAggregate.setFreightPrice(new LitemallMoney(freightPrice));
        orderAggregate.setCouponPrice(new LitemallMoney(couponPrice));
        orderAggregate.setIntegralPrice(new LitemallMoney(integralPrice));
        orderAggregate.setOrderPrice(new LitemallMoney(orderTotalPrice));
        orderAggregate.setActualPrice(new LitemallMoney(actualPrice));

        if(grouponRulesAggregate != null){
            orderAggregate.setGrouponPrice(new LitemallMoney(grouponPrice));
        }else {
            orderAggregate.setGrouponPrice(new LitemallMoney(new BigDecimal(0)));
        }
        // Save the order
        orderRepository.addOrder(orderAggregate);

        // Get the id from the order
        LitemallOrderAggregate existingOrderAggregate = orderRepository.findById(orderAggregate.getOrderId()).orElseThrow(() -> new NoSuchElementException("Order not found"));

        // Add the order goods items information
        for(LitemallCartAggregate cartGoods: cartList){

            LitemallOrderGoodsAggregate orderGoodsAggregate = new LitemallOrderGoodsAggregate();
            orderGoodsAggregate.setOrderId(existingOrderAggregate.getOrderId());
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
        clearCart(cmdUserId, new LitemallCartId(command.getCartId()));

        // Reduce the product stock
        validateAndReduceStock(cartList);

        // Update coupon usage if applicable
        if (command.getCouponId() != 0 && command.getCouponId() != -1) {
            LitemallCouponUserAggregate couponUserAggregate = couponService.getUserCouponById(new LitemallCouponUserId(command.getUserCouponId()));
            //couponUserAggregate.setStatus(CouponUserConstant.STATUS_USED);
            couponUserAggregate.setStatus(LitemallCouponUserStatus.USED);
            couponUserAggregate.setUsedTime(LocalDateTime.now());
            couponUserAggregate.setOrderId(newOrderId);
            couponService.updateCouponUser(couponUserAggregate);
        }

        // If it's a groupon purchase project, add group buying information
        Integer grouponLinkId = grouponServiceLayer.createGrouponOrder(
                command.getGrouponLinkId(), cmdUserId.getId(), cmdGrouponRulesId.getId(), existingOrderAggregate.getOrderId());

        if (grouponLinkId != null) {
            // Handle groupon-specific logic if needed
            log.info("Groupon order created with link ID: {}", grouponLinkId);
        }


        //publish domain events

        //Validate and process groupon if available
        return new LitemallOrderSubmitResult(
                existingOrderAggregate.getOrderId().getId(),
                existingOrderAggregate.getOrderSn(),
                false, // payment handled by orchestrator
                command.getGrouponLinkId(),
                existingOrderAggregate.getActualPrice().getAmount(),
                LocalDateTime.now(),
                LitemallOrderSubmitResult.LitemallOrderSubmitResultStatus.SUCCESS
        );
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


   /* private LitemallGrouponAggregate handleGrouponCreation(LitemallPlaceOrderCommand command, LitemallOrderAggregateRoot orderAggregateRoot, LitemallGrouponRulesAggregate rulesAggregate) {

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

    }*/

    public void publishDomainEvents(LitemallGrouponAggregate grouponAggregate){
        grouponAggregate.getDomainEvents().forEach(this.domainEventPublisher::publish);

        grouponAggregate.getDomainEvents().forEach(this.domainEventPublisher::publish);
    }

    public Optional<LitemallOrderAggregate> getOrderAggregate(LitemallOrderId orderId) {
        return orderRepository.findById(orderId);
    }

    public void cancelOrder(LitemallOrderId orderId, String reason) {
        LitemallOrderAggregate orderAggregate =  orderRepository.findById(orderId).orElseThrow(() -> new NoSuchElementException("Order not found"));

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

    /**
     *
     * @param orderId
     */
    private void updateOrderStatusToPaid(LitemallOrderId orderId) {
        LitemallOrderAggregate paidOrder = new LitemallOrderAggregate();
        paidOrder.setOrderId(orderId);
        paidOrder.setOrderStatus(LitemallOrderStatus.PAID);
        orderRepository.updateSelective(paidOrder);
    }
    /**
     * @Desc: Validate and reduce stock for all items in a batch
     * @param cartList
     */
    private void validateAndReduceStock(List<LitemallCartAggregate> cartList) {
        // STEP 1: Load all required data in batch
        AggregatesValidationContext context = loadAggregatesValidationContext(cartList);

        if (!context.isValidForValidation()) {
            throw new RuntimeException("Failed to load required product data for validation");
        }

        // STEP 2: Validate stock for all items
        validateStockForAllItems(cartList, context);

        // STEP 3: Reduce stock for all items in batch
        reduceStockForAllItems(cartList, context);
    }
    /**
     *
     * @param cartList
     * @return
     */
    private AggregatesValidationContext loadAggregatesValidationContext(List<LitemallCartAggregate> cartList){

        try {
            //Extract all required IDs from one pass
            Set<Integer> goodsIds = cartList.stream()
                    .map(item -> item.getGoodsId().getId())
                    .collect(Collectors.toSet());

            Set<Integer> productIds = cartList.stream()
                    .map(item -> item.getProductId().getId())
                    .collect(Collectors.toSet());

            // Batch load all data in parallel
            CompletableFuture<Map<LitemallGoodsId, LitemallGoodsAggregate>> goodsFuture =
                    CompletableFuture.supplyAsync(() ->
                           batchGetGoodsAggregates(goodsIds));

            CompletableFuture<Map<LitemallGoodsProductId, LitemallGoodsProductAggregate>> productsFuture =
                    CompletableFuture.supplyAsync(() ->
                            batchGetProductAggregates(productIds));

            // Wait for all batch requests to complete
            //Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap = goodsFuture.get();
            Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap = goodsFuture.join();
            Map<LitemallGoodsProductId, LitemallGoodsProductAggregate> productsMap = productsFuture.join();

            return AggregatesValidationContext.create(goodsMap, productsMap);


        } catch (Exception e) {
            // Handle exceptions gracefully
            log.error("Failed to load goods from the feign client validation context for cartList {}",
                  cartList, e);
            return AggregatesValidationContext.mapsGoodsAndMapsProductsNotFound();
        }
    }

    private Map<LitemallGoodsId, LitemallGoodsAggregate> convertToDomainGoodsMap(Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap) {
        return goodsMap.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> new LitemallGoodsId(entry.getKey().getId()),
                        Map.Entry::getValue
                ));
    }

    private Map<LitemallGoodsId, LitemallGoodsAggregate> batchGetGoodsAggregates(Set<Integer> goodsIds) {
        if (goodsIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // Single batch call instead of N individual calls
        Map<LitemallGoodsId, LitemallGoodsAggregate> response = null;
        try {
            response = FeignResponseHandler.handleResponse(
                    goodsServiceFeignClient.batchGetGoodsAggregates(new BatchGoodsRequest(goodsIds)),
                    "Batch Get Goods Operation"
            );
        } catch (ServiceException e) {
            throw new RuntimeException(e);
        }

        // Convert back to domain IDs
       /* return response.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> new LitemallGoodsId(entry.getKey()),
                        Map.Entry::getValue
                ));*/
        return response;
    }

    private Map<LitemallGoodsProductId, LitemallGoodsProductAggregate> batchGetProductAggregates(Set<Integer> productIds) {
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<LitemallGoodsProductId, LitemallGoodsProductAggregate> response = null;
        try {
            response = FeignResponseHandler.handleResponse(
                    goodsServiceFeignClient.batchGetGoodsProductsAggregate(new BatchProductsRequest(productIds)),
                    "Batch Get Products Operation"
            );
        } catch (ServiceException e) {
            throw new RuntimeException(e);
        }
        return response;
    }

    private void validateStockForAllItems(List<LitemallCartAggregate> cartList, AggregatesValidationContext context){
        List<String> outOfStockItems = new ArrayList<>();

        for (LitemallCartAggregate cartItem : cartList) {
            LitemallGoodsProductAggregate product = context.getProduct(cartItem.getProductId());

            int remainNumber = product.getNumber() - cartItem.getNumber();
            if (remainNumber < 0) {
                LitemallGoodsAggregate goods = context.getGoods(cartItem.getGoodsId());
                outOfStockItems.add(String.format(
                        "Product %s (ID: %d): requested %d, available %d",
                        goods.getGoodsName(),
                        product.getGoodsProductId().getId(),
                        cartItem.getNumber(),
                        product.getNumber()
                ));
            }
        }

        if (!outOfStockItems.isEmpty()) {
            String errorMessage = "Insufficient stock for products:\n" +
                    String.join("\n", outOfStockItems);
            throw new RuntimeException(errorMessage);
        }
    }

    private void reduceStockForAllItems(List<LitemallCartAggregate> cartList, AggregatesValidationContext context){

        // Prepare batch reduce stock requests
        List<ReduceStockRequest> reduceStockRequests = cartList.stream()
                .map(cartItem -> new ReduceStockRequest(
                        cartItem.getProductId().getId(),
                        cartItem.getNumber()
                ))
                .toList();

        // Single batch call instead of N individual calls
        Map<Integer, Boolean> reduceResults = null;
        try {
             reduceResults = FeignResponseHandler.handleResponse(
                    goodsServiceFeignClient.batchReduceStock(reduceStockRequests),
                    "Batch Reduce Stock Operation"
            );
        } catch (ServiceException e) {
            throw new RuntimeException(e);
        }
        // Verify all reductions were successful
        List<Integer> failedReductions = reduceResults.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .toList();

        if (!failedReductions.isEmpty()) {
            throw new RuntimeException("Stock reduction failed for product IDs: " + failedReductions);
        }

    }


}
