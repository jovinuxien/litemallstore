package org.linlinjava.litemall.wx.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.wx.annotation.LoginUser;
import org.linlinjava.litemall.wx.service.WxOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;

@RestController
@RequestMapping("/wx/order")
@Validated
public class WxOrderController {
    private final Log logger = LogFactory.getLog(WxOrderController.class);

    @Autowired
    private WxOrderService wxOrderService;

    /**
     * Order list
     *
     * @param userId user ID
     * @param showType display type, if it is 0, it is all orders
     * @param page paging page number
     * @param limit paging size
     * @param sort sort field
     * @param order sorting method
     * @return order list
     */
    @GetMapping("list")
    public Object list(@LoginUser Integer userId,
                       @RequestParam(defaultValue = "0") Integer showType,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit,
                       @Sort @RequestParam(defaultValue = "add_time") String sort,
                       @Order @RequestParam(defaultValue = "desc") String order) {
        return wxOrderService.list(userId, showType, page, limit, sort, order);
    }

    /**
     * 订单详情
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @return 订单详情
     */
    @GetMapping("detail")
    public Object detail(@LoginUser Integer userId, @NotNull Integer orderId) {
        return wxOrderService.detail(userId, orderId);
    }

    /**
     * 提交订单
     *
     * @param userId User ID
     * @param body   Order information，{ cartId：xxx, addressId: xxx, couponId: xxx, message: xxx, grouponRulesId: xxx,  grouponLinkId: xxx}
     * @return Submit order operation results
     */
    @PostMapping("submit")
    public Object submit(@LoginUser Integer userId, @RequestBody String body) {
        return wxOrderService.submit(userId, body);
    }

    /**
     * Cancel order
     *
     * @param userId user ID
     * @param body order information, { orderId: xxx }
     * @return Cancel order operation result
     */
    @PostMapping("cancel")
    public Object cancel(@LoginUser Integer userId, @RequestBody String body) {
        return wxOrderService.cancel(userId, body);
    }

    /**
     * The prepayment session ID of the payment order
     *
     * @param userId user ID
     * @param body order information, { orderId: xxx }
     * @return payment order ID
     */
    @PostMapping("prepay")
    public Object prepay(@LoginUser Integer userId, @RequestBody String body, HttpServletRequest request) {
        return wxOrderService.prepay(userId, body, request);
    }

    /**
     * WeChat H5 payment
     * @param userId
     * @param body
     * @param request
     * @return
     */
    @PostMapping("h5pay")
    public Object h5pay(@LoginUser Integer userId, @RequestBody String body, HttpServletRequest request) {
        return wxOrderService.h5pay(userId, body, request);
    }

    /**
     * WeChat payment success or failure callback interface
     * <p>
     *TODO
     * Note that pay-notify here is an example address. It is recommended that developers set up a hidden callback address.
     *
     * @param request request content
     * @param response response content
     * @return operation result
     */
    @PostMapping("pay-notify")
    public Object payNotify(HttpServletRequest request, HttpServletResponse response) {
        return wxOrderService.payNotify(request, response);
    }

    /**
     * Apply for a refund on your order
     *
     * @param userId user ID
     * @param body order information, { orderId: xxx }
     * @return Order refund operation result
     */
    @PostMapping("refund")
    public Object refund(@LoginUser Integer userId, @RequestBody String body) {
        return wxOrderService.refund(userId, body);
    }

    /**
     * confirm the receipt of goods
     *
     * @param userId user ID
     * @param body order information, { orderId: xxx }
     * @return order operation result
     */
    @PostMapping("confirm")
    public Object confirm(@LoginUser Integer userId, @RequestBody String body) {
        return wxOrderService.confirm(userId, body);
    }

    /**
     * Delete order
     *
     * @param userId user ID
     * @param body order information, { orderId: xxx }
     * @return order operation result
     */
    @PostMapping("delete")
    public Object delete(@LoginUser Integer userId, @RequestBody String body) {
        return wxOrderService.delete(userId, body);
    }

    /**
     * Product information of orders to be evaluated
     *
     * @param userId user ID
     * @param ogid order item ID
     * @return Order product information to be evaluated
     */
    @GetMapping("goods")
    public Object goods(@LoginUser Integer userId,
                        @NotNull Integer ogid) {
        return wxOrderService.goods(userId, ogid);
    }

    /**
     * Evaluate order items
     *
     * @param userId user ID
     * @param body order information, { orderId: xxx }
     * @return order operation result
     */
    @PostMapping("comment")
    public Object comment(@LoginUser Integer userId, @RequestBody String body) {
        return wxOrderService.comment(userId, body);
    }

}