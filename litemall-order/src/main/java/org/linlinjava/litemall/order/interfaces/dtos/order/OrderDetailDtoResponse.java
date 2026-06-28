package org.linlinjava.litemall.order.interfaces.dtos.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.util.LitemallOrderHandleOption;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Full customer order detail, shaped after the SPA {@code IOrderDetail} model:
 * shipping recipient, the price breakdown, and the line items. Prices are plain
 * numbers ({@code amount}); {@code addTime} serialises ISO-8601.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class OrderDetailDtoResponse {

    private final Integer id;
    private final String orderSn;
    private final LocalDateTime addTime;
    private final String consignee;
    private final String mobile;
    private final String address;
    private final String orderStatusText;
    private final OrderHandleOptionDtoResponse handleOption;
    private final BigDecimal goodsPrice;
    private final BigDecimal freightPrice;
    private final BigDecimal couponPrice;
    private final BigDecimal actualPrice;
    private final List<OrderGoodsDtoResponse> orderGoods;

    public OrderDetailDtoResponse(Integer id, String orderSn, LocalDateTime addTime, String consignee,
                                  String mobile, String address, String orderStatusText,
                                  OrderHandleOptionDtoResponse handleOption, BigDecimal goodsPrice,
                                  BigDecimal freightPrice, BigDecimal couponPrice, BigDecimal actualPrice,
                                  List<OrderGoodsDtoResponse> orderGoods) {
        this.id = id;
        this.orderSn = orderSn;
        this.addTime = addTime;
        this.consignee = consignee;
        this.mobile = mobile;
        this.address = address;
        this.orderStatusText = orderStatusText;
        this.handleOption = handleOption;
        this.goodsPrice = goodsPrice;
        this.freightPrice = freightPrice;
        this.couponPrice = couponPrice;
        this.actualPrice = actualPrice;
        this.orderGoods = orderGoods;
    }

    public static OrderDetailDtoResponse fromDomain(LitemallOrderAggregate o,
                                                    List<LitemallOrderGoodsAggregate> goods) {
        return new OrderDetailDtoResponse(
                o.getOrderId() == null ? null : o.getOrderId().getId(),
                o.getOrderSn(),
                o.getAddTime(),
                o.getConsignee(),
                o.getMobile(),
                o.getAddress(),
                OrderStatusText.of(o.getOrderStatus()),
                OrderHandleOptionDtoResponse.fromDomain(LitemallOrderHandleOption.forStatus(o.getOrderStatus())),
                o.getGoodsPrice() == null ? null : o.getGoodsPrice().getAmount(),
                o.getFreightPrice() == null ? null : o.getFreightPrice().getAmount(),
                o.getCouponPrice() == null ? null : o.getCouponPrice().getAmount(),
                o.getActualPrice() == null ? null : o.getActualPrice().getAmount(),
                goods == null ? List.of()
                        : goods.stream().map(OrderGoodsDtoResponse::fromDomain).collect(Collectors.toList()));
    }
}
