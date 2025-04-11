package org.linlinjava.litemall.wx.web;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.core.util.JacksonUtil;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.*;
import org.linlinjava.litemall.db.service.*;
import org.linlinjava.litemall.wx.annotation.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

import static org.linlinjava.litemall.wx.util.WxResponseCode.GOODS_NO_STOCK;
import static org.linlinjava.litemall.wx.util.WxResponseCode.GOODS_UNSHELVE;

/**
 * User shopping cart service
 */
@RestController
@RequestMapping("/wx/cart")
@Validated
public class WxCartController {
    private final Log logger = LogFactory.getLog(WxCartController.class);

    @Autowired
    private LitemallCartService cartService;
    @Autowired
    private LitemallGoodsService goodsService;
    @Autowired
    private LitemallGoodsProductService productService;
    @Autowired
    private LitemallAddressService addressService;
    @Autowired
    private LitemallGrouponRulesService grouponRulesService;
    @Autowired
    private LitemallCouponService couponService;
    @Autowired
    private LitemallCouponUserService couponUserService;
    @Autowired
    private CouponVerifyService couponVerifyService;

    /**
     * User shopping cart information
     *
     * @param userId User ID
     * @return User shopping cart information
     */
    @GetMapping("index")
    public Object index(@LoginUser Integer userId) {


        /**
         * Here i edited the logic where the user is no logged in
         * by commenting out // return ResponseUtil.unlogin() method
         * so that we can get the index of the cart content when the user is not logged in
         */
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        List<LitemallCart> list = cartService.queryByUid(userId);
        List<LitemallCart> cartList = new ArrayList<>();
        // TODO
        // If the system checks that the product has been deleted or taken off the shelves，The system will automatically delete
        // A better effect would be to inform the user that the product has expired，Allow users to click a button to clear expired items
        for (LitemallCart cart : list) {
            LitemallGoods goods = goodsService.findById(cart.getGoodsId());
            if (goods == null || !goods.getIsOnSale()) {
                cartService.deleteById(cart.getId());
                logger.debug("The system automatically deletes invalid shopping cart items goodsId=" + cart.getGoodsId() + " productId=" + cart.getProductId());
            }
            else{
                cartList.add(cart);
            }
        }

        Integer goodsCount = 0;
        BigDecimal goodsAmount = new BigDecimal(0.00);
        Integer checkedGoodsCount = 0;
        BigDecimal checkedGoodsAmount = new BigDecimal(0.00);
        for (LitemallCart cart : cartList) {
            goodsCount += cart.getNumber();
            goodsAmount = goodsAmount.add(cart.getPrice().multiply(new BigDecimal(cart.getNumber())));
            if (cart.getChecked()) {
                checkedGoodsCount += cart.getNumber();
                checkedGoodsAmount = checkedGoodsAmount.add(cart.getPrice().multiply(new BigDecimal(cart.getNumber())));
            }   
        }
        Map<String, Object> cartTotal = new HashMap<>();
        cartTotal.put("goodsCount", goodsCount);
        cartTotal.put("goodsAmount", goodsAmount);
        cartTotal.put("checkedGoodsCount", checkedGoodsCount);
        cartTotal.put("checkedGoodsAmount", checkedGoodsAmount);

        Map<String, Object> result = new HashMap<>();
        result.put("cartList", cartList);
        result.put("cartTotal", cartTotal);

        return ResponseUtil.ok(result);
    }

    /**
     * Add items to shopping cart
     * <p>
     * If there are already items in the shopping cart, add the quantity;
     * Otherwise add a new shopping cart item.
     *
     * @param userId user ID
     * @param cart shopping cart product information, { goodsId: xxx, productId: xxx, number: xxx }
     * @return Add to cart operation result
     */
    @PostMapping("add")
    public Object add(@LoginUser Integer userId, @RequestBody LitemallCart cart) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        if (cart == null) {
            return ResponseUtil.badArgument();
        }

        Integer productId = cart.getProductId();
        Integer number = cart.getNumber().intValue();
        Integer goodsId = cart.getGoodsId();
        if (!ObjectUtils.allNotNull(productId, number, goodsId)) {
            return ResponseUtil.badArgument();
        }
        if(number <= 0){
            return ResponseUtil.badArgument();
        }

        //Determine whether the product is available for purchase
        LitemallGoods goods = goodsService.findById(goodsId);
        if (goods == null || !goods.getIsOnSale()) {
            return ResponseUtil.fail(GOODS_UNSHELVE, "The product has been removed from the shelves");
        }

        LitemallGoodsProduct product = productService.findById(productId);
        //Determine whether the product of this specification exists in the shopping cart
        LitemallCart existCart = cartService.queryExist(goodsId, productId, userId);
        if (existCart == null) {
            //Get specification information,Determine specification inventory
            if (product == null || number > product.getNumber()) {
                return ResponseUtil.fail(GOODS_NO_STOCK, "Insufficient stock");
            }

            cart.setId(null);
            cart.setGoodsSn(goods.getGoodsSn());
            cart.setGoodsName((goods.getName()));
            if(StringUtils.isEmpty(product.getUrl())){
                cart.setPicUrl(goods.getPicUrl());
            }
            else{
                cart.setPicUrl(product.getUrl());
            }
            cart.setPrice(product.getPrice());
            cart.setSpecifications(product.getSpecifications());
            cart.setUserId(userId);
            cart.setChecked(true);
            cartService.add(cart);
        } else {
            //Get specification information,Determine specification inventory
            int num = existCart.getNumber() + number;
            if (num > product.getNumber()) {
                return ResponseUtil.fail(GOODS_NO_STOCK, "Insufficient stock");
            }
            existCart.setNumber((short) num);
            if (cartService.updateById(existCart) == 0) {
                return ResponseUtil.updatedDataFailed();
            }
        }

        return goodscount(userId);
    }

    @PostMapping("addNoId")
    public Object addNoId(@LoginUser Integer userId, @RequestBody LitemallCart cart) {
        if (cart == null) {
            return ResponseUtil.badArgument();
        }

        Integer productId = cart.getProductId();
        Integer number = cart.getNumber().intValue();
        Integer goodsId = cart.getGoodsId();
        if (!ObjectUtils.allNotNull(productId, number, goodsId)) {
            return ResponseUtil.badArgument();
        }
        if(number <= 0){
            return ResponseUtil.badArgument();
        }

        //Determine whether the product is available for purchase
        LitemallGoods goods = goodsService.findById(goodsId);
        if (goods == null || !goods.getIsOnSale()) {
            return ResponseUtil.fail(GOODS_UNSHELVE, "The product has been removed from the shelves");
        }

        LitemallGoodsProduct product = productService.findById(productId);
        if (product == null || number > product.getNumber()) {
            return ResponseUtil.fail(GOODS_NO_STOCK, "Insufficient stock");
        }

        // If userId is null, we'll create a temporary cart item
        if (userId == null) {
            LitemallCart tempCart = new LitemallCart();
            tempCart.setGoodsSn(goods.getGoodsSn());
            tempCart.setGoodsName(goods.getName());
            tempCart.setPicUrl(StringUtils.isEmpty(product.getUrl()) ? goods.getPicUrl() : product.getUrl());
            tempCart.setPrice(product.getPrice());
            tempCart.setSpecifications(product.getSpecifications());
            tempCart.setNumber(number.shortValue());
            tempCart.setChecked(true);

            Map<String, Object> result = new HashMap<>();
            result.put("cartItem", tempCart);
            return ResponseUtil.ok(result);
        } else {
            // If userId is not null, proceed with the original logic
            LitemallCart existCart = cartService.queryExist(goodsId, productId, userId);
            if (existCart == null) {
                cart.setId(null);
                cart.setGoodsSn(goods.getGoodsSn());
                cart.setGoodsName(goods.getName());
                cart.setPicUrl(StringUtils.isEmpty(product.getUrl()) ? goods.getPicUrl() : product.getUrl());
                cart.setPrice(product.getPrice());
                cart.setSpecifications(product.getSpecifications());
                cart.setUserId(userId);
                cart.setChecked(true);
                cartService.add(cart);
            } else {
                int num = existCart.getNumber() + number;
                if (num > product.getNumber()) {
                    return ResponseUtil.fail(GOODS_NO_STOCK, "Insufficient stock");
                }
                existCart.setNumber((short) num);
                if (cartService.updateById(existCart) == 0) {
                    return ResponseUtil.updatedDataFailed();
                }
            }

            return goodscount(userId);
        }
    }

    /**
     * Buy it now
     * <p>
     The difference between * and add method is:
     * 1. If there are already items in the shopping cart, the logic of the former is to add the quantity, and the logic here is to overwrite the quantity.
     * 2. After the addition is successful, the logic of the former is to return the number of items in the current shopping cart, and the logic here is to return the ID of the corresponding shopping cart item.
     *
     * @param userId user ID
     * @param cart shopping cart product information, { goodsId: xxx, productId: xxx, number: xxx }
     * @return Buy now operation result
     */
    @PostMapping("fastadd")
    public Object fastadd(@LoginUser Integer userId, @RequestBody LitemallCart cart) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        if (cart == null) {
            return ResponseUtil.badArgument();
        }

        Integer productId = cart.getProductId();
        Integer number = cart.getNumber().intValue();
        Integer goodsId = cart.getGoodsId();
        if (!ObjectUtils.allNotNull(productId, number, goodsId)) {
            return ResponseUtil.badArgument();
        }
        if(number <= 0){
            return ResponseUtil.badArgument();
        }

        //Determine whether the product is available for purchase
        LitemallGoods goods = goodsService.findById(goodsId);
        if (goods == null || !goods.getIsOnSale()) {
            return ResponseUtil.fail(GOODS_UNSHELVE, "The product has been removed from the shelves");
        }

        LitemallGoodsProduct product = productService.findById(productId);
        //Determine whether the product of this specification exists in the shopping cart
        LitemallCart existCart = cartService.queryExist(goodsId, productId, userId);
        if (existCart == null) {
            //Obtain specification information and determine specification inventory
            if (product == null || number > product.getNumber()) {
                return ResponseUtil.fail(GOODS_NO_STOCK, "Insufficient stock");
            }

            cart.setId(null);
            cart.setGoodsSn(goods.getGoodsSn());
            cart.setGoodsName((goods.getName()));
            if(StringUtils.isEmpty(product.getUrl())){
                cart.setPicUrl(goods.getPicUrl());
            }
            else{
                cart.setPicUrl(product.getUrl());
            }
            cart.setPrice(product.getPrice());
            cart.setSpecifications(product.getSpecifications());
            cart.setUserId(userId);
            cart.setChecked(true);
            cartService.add(cart);
        } else {
            //Obtain specification information and determine specification inventory
            int num = number;
            if (num > product.getNumber()) {
                return ResponseUtil.fail(GOODS_NO_STOCK, "Insufficient stock");
            }
            existCart.setNumber((short) num);
            if (cartService.updateById(existCart) == 0) {
                return ResponseUtil.updatedDataFailed();
            }
        }

        return ResponseUtil.ok(existCart != null ? existCart.getId() : cart.getId());
    }

    /**
     * Modify the quantity of items in the shopping cart
     *
     * @param userId user ID
     * @param cart Shopping cart product information, { id: xxx, goodsId: xxx, productId: xxx, number: xxx }
     * @return modified result
     */
    @PostMapping("update")
    public Object update(@LoginUser Integer userId, @RequestBody LitemallCart cart) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Integer productId = cart.getProductId();
        Integer number = cart.getNumber().intValue();
        Integer goodsId = cart.getGoodsId();
        Integer id = cart.getId();
        if (!ObjectUtils.allNotNull(id, productId, number, goodsId)) {
            return ResponseUtil.badArgument();
        }
        if(number <= 0){
            return ResponseUtil.badArgument();
        }

        //Determine whether the order exists
        // If it does not exist, return an error directly
        LitemallCart existCart = cartService.findById(userId, id);
        if (existCart == null) {
            return ResponseUtil.badArgumentValue();
        }

        // Determine whether goodsId and productId are consistent with the values in the current cart
        if (!existCart.getGoodsId().equals(goodsId)) {
            return ResponseUtil.badArgumentValue();
        }
        if (!existCart.getProductId().equals(productId)) {
            return ResponseUtil.badArgumentValue();
        }

        //Determine whether the product is available for purchase
        LitemallGoods goods = goodsService.findById(goodsId);
        if (goods == null || !goods.getIsOnSale()) {
            return ResponseUtil.fail(GOODS_UNSHELVE, "The product has been removed from the shelves");
        }

        //Obtain specification information and determine specification inventory
        LitemallGoodsProduct product = productService.findById(productId);
        if (product == null || product.getNumber() < number) {
            return ResponseUtil.fail(GOODS_UNSHELVE, "Insufficient stock");
        }

        existCart.setNumber(number.shortValue());
        if (cartService.updateById(existCart) == 0) {
            return ResponseUtil.updatedDataFailed();
        }
        return ResponseUtil.ok();
    }

    /**
     * Shopping cart item check status
     * <p>
     * If the product is not checked originally, set the checked status; if the product is already checked, set the unchecked status.
     *
     * @param userId user ID
     * @param body shopping cart product information, { productIds: xxx, isChecked: 1/0 }
     * @return shopping cart information
     */
    @PostMapping("checked")
    public Object checked(@LoginUser Integer userId, @RequestBody String body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        if (body == null) {
            return ResponseUtil.badArgument();
        }

        List<Integer> productIds = JacksonUtil.parseIntegerList(body, "productIds");
        if (productIds == null) {
            return ResponseUtil.badArgument();
        }

        Integer checkValue = JacksonUtil.parseInteger(body, "isChecked");
        if (checkValue == null) {
            return ResponseUtil.badArgument();
        }
        Boolean isChecked = (checkValue == 1);

        cartService.updateCheck(userId, productIds, isChecked);
        return index(userId);
    }

    /**
     *Delete shopping cart items
     *
     * @param userId user ID
     * @param body shopping cart product information, { productIds: xxx }
     * @return shopping cart information
     * If successful
     * {
     * errno: 0,
     * errmsg: 'Success',
     *data:xxx
     * }
     * If failed, { errno: XXX, errmsg: XXX }
     */
    @PostMapping("delete")
    public Object delete(@LoginUser Integer userId, @RequestBody String body) {

        if (body == null) {
            return ResponseUtil.badArgument();
        }

        /**
         * Here i edited the logic where the user is no logged in
         * by commenting out // return ResponseUtil.unlogin() method
         * so that we can delete the cart content when the user is not logged in
         */
        if (userId == null) {
            //return ResponseUtil.unlogin();
            List<Integer> productIds = JacksonUtil.parseIntegerList(body, "productIds");
            if (productIds == null || productIds.isEmpty()) {
                return ResponseUtil.badArgument();
            }
            cartService.delete(productIds, userId);
            return ResponseUtil.ok();
        }

        List<Integer> productIds = JacksonUtil.parseIntegerList(body, "productIds");

        if (productIds == null || productIds.isEmpty()) {
            return ResponseUtil.badArgument();
        }

        cartService.delete(productIds, userId);
        return index(userId);
    }

    /**
     * Quantity of items in shopping cart
     * <p>
     * If the user is not logged in, empty data is returned.
     *
     * @param userId user ID
     * @return The quantity of items in the shopping cart
     */
    @GetMapping("goodscount")
    public Object goodscount(@LoginUser Integer userId) {
        if (userId == null) {
            return ResponseUtil.ok(0);
        }

        int goodsCount = 0;
        List<LitemallCart> cartList = cartService.queryByUid(userId);
        for (LitemallCart cart : cartList) {
            goodsCount += cart.getNumber();
        }

        return ResponseUtil.ok(goodsCount);
    }

    /**
     * Order in shopping cart
     *
     * @param userId user ID
     * @param cartId Shopping cart product ID:
     * If the shopping cart product ID is empty, place an order for all shopping cart products of the current user;
     * If the shopping cart product ID is not empty, only the current shopping cart product will be ordered.
     * @param addressId Shipping address ID:
     * If the delivery address ID is empty, query the current user's default address.
     * @param couponId Coupon ID:
     * If the coupon ID is empty, the appropriate coupon is automatically selected.
     * @return shopping cart operation result
     */
    @GetMapping("checkout")
    public Object checkout(@LoginUser Integer userId, Integer cartId, Integer addressId, Integer couponId, Integer userCouponId, Integer grouponRulesId) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        // Shipping address
        LitemallAddress checkedAddress = null;
        if (addressId != null && !addressId.equals(0)) {
            checkedAddress = addressService.query(userId, addressId);
        }
        if (checkedAddress == null) {
            checkedAddress = addressService.findDefault(userId);
            // If there is still no address, there is no shipping address
            // Return an empty address id=0, so the front end will remind you to add the address
            if (checkedAddress == null) {
                checkedAddress = new LitemallAddress();
                checkedAddress.setId(0);
                addressId = 0;
            } else {
                addressId = checkedAddress.getId();
            }
        }

        // Group purchase discount
        BigDecimal grouponPrice = new BigDecimal(0.00);
        LitemallGrouponRules grouponRules = grouponRulesService.findById(grouponRulesId);
        if (grouponRules != null) {
            grouponPrice = grouponRules.getDiscount();
        }

        // Product price
        List<LitemallCart> checkedGoodsList = null;
        if (cartId == null || cartId.equals(0)) {
            checkedGoodsList = cartService.queryByUidAndChecked(userId);
        } else {
            LitemallCart cart = cartService.findById(userId, cartId);
            if (cart == null) {
                return ResponseUtil.badArgumentValue();
            }
            checkedGoodsList = new ArrayList<>(1);
            checkedGoodsList.add(cart);
        }
        BigDecimal checkedGoodsPrice = new BigDecimal(0.00);
        for (LitemallCart cart : checkedGoodsList) {
            //  Only when the product ID meets the group purchase specifications will the group purchase discount be available
            if (grouponRules != null && grouponRules.getGoodsId().equals(cart.getGoodsId())) {
                checkedGoodsPrice = checkedGoodsPrice.add(cart.getPrice().subtract(grouponPrice).multiply(new BigDecimal(cart.getNumber())));
            } else {
                checkedGoodsPrice = checkedGoodsPrice.add(cart.getPrice().multiply(new BigDecimal(cart.getNumber())));
            }
        }

        // Calculate coupon availability
        BigDecimal tmpCouponPrice = new BigDecimal(0.00);
        Integer tmpCouponId = 0;
        Integer tmpUserCouponId = 0;
        int tmpCouponLength = 0;
        List<LitemallCouponUser> couponUserList = couponUserService.queryAll(userId);
        for(LitemallCouponUser couponUser : couponUserList){
            LitemallCoupon coupon = couponVerifyService.checkCoupon(userId, couponUser.getCouponId(), couponUser.getId(), checkedGoodsPrice, checkedGoodsList);
            if(coupon == null){
                continue;
            }

            tmpCouponLength++;
            if(tmpCouponPrice.compareTo(coupon.getDiscount()) == -1){
                tmpCouponPrice = coupon.getDiscount();
                tmpCouponId = coupon.getId();
                tmpUserCouponId = couponUser.getId();
            }
        }
        // Get the coupon reduction amount and the available number of coupons
        int availableCouponLength = tmpCouponLength;
        BigDecimal couponPrice = new BigDecimal(0);
        //There are three situations here
        // 1. If the user does not want to use the coupon, it will not be processed.
        // 2. If the user wants to automatically use the coupon, select the appropriate coupon
        // 3. If the user has selected a coupon, test whether the coupon is appropriate.
        if (couponId == null || couponId.equals(-1)){
            couponId = -1;
            userCouponId = -1;
        }
        else if (couponId.equals(0)) {
            couponPrice = tmpCouponPrice;
            couponId = tmpCouponId;
            userCouponId = tmpUserCouponId;
        }
        else {
            LitemallCoupon coupon = couponVerifyService.checkCoupon(userId, couponId, userCouponId, checkedGoodsPrice, checkedGoodsList);
            // If there is a problem with the coupon selected by the user, select the appropriate coupon, otherwise use the coupon selected by the user
            // If there is a problem with the coupon selected by the user, select the appropriate coupon, otherwise use the coupon selected by the user
            if(coupon == null){
                couponPrice = tmpCouponPrice;
                couponId = tmpCouponId;
                userCouponId = tmpUserCouponId;
            }
            else {
                couponPrice = coupon.getDiscount();
            }
        }

        //The shipping fee is calculated based on the total price of the order. Free shipping for orders over 88 yuan, otherwise 8 yuan;
        BigDecimal freightPrice = new BigDecimal(0.00);
        if (checkedGoodsPrice.compareTo(SystemConfig.getFreightLimit()) < 0) {
            freightPrice = SystemConfig.getFreight();
        }

        // Other money that can be used, such as user points
        BigDecimal integralPrice = new BigDecimal(0.00);

        // Order fee
        BigDecimal orderTotalPrice = checkedGoodsPrice.add(freightPrice).subtract(couponPrice).max(new BigDecimal(0.00));

        BigDecimal actualPrice = orderTotalPrice.subtract(integralPrice);

        Map<String, Object> data = new HashMap<>();
        data.put("addressId", addressId);
        data.put("couponId", couponId);
        data.put("userCouponId", userCouponId);
        data.put("cartId", cartId);
        data.put("grouponRulesId", grouponRulesId);
        data.put("grouponPrice", grouponPrice);
        data.put("checkedAddress", checkedAddress);
        data.put("availableCouponLength", availableCouponLength);
        data.put("goodsTotalPrice", checkedGoodsPrice);
        data.put("freightPrice", freightPrice);
        data.put("couponPrice", couponPrice);
        data.put("orderTotalPrice", orderTotalPrice);
        data.put("actualPrice", actualPrice);
        data.put("checkedGoodsList", checkedGoodsList);
        return ResponseUtil.ok(data);
    }
}
