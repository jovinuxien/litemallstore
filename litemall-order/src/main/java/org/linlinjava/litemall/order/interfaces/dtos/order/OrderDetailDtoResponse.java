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
    // Fulfillment origin ('local' | 'cj') + CJ references once placed, so the SPA can
    // badge dropship orders and show/track the CJ order.
    private final String source;
    private final String cjOrderId;
    private final String cjOrderNum;
    /** Logistics line the order ships with (CJ line at placement / admin ship channel). */
    private final String shipChannel;
    // In-store pickup (Wave 4): mode + store + the redeem code. verifyCode is only ever
    // serialized on the OWNER-scoped detail read (this DTO) — paid orders only (null
    // until pay). NON_NULL keeps express orders' payloads unchanged.
    private final String deliveryType;
    private final Integer storeId;
    private final String verifyCode;
    private final LocalDateTime verifyTime;

    public OrderDetailDtoResponse(Integer id, String orderSn, LocalDateTime addTime, String consignee,
                                  String mobile, String address, String orderStatusText,
                                  OrderHandleOptionDtoResponse handleOption, BigDecimal goodsPrice,
                                  BigDecimal freightPrice, BigDecimal couponPrice, BigDecimal actualPrice,
                                  List<OrderGoodsDtoResponse> orderGoods,
                                  String source, String cjOrderId, String cjOrderNum, String shipChannel,
                                  String deliveryType, Integer storeId, String verifyCode,
                                  LocalDateTime verifyTime) {
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
        this.source = source;
        this.cjOrderId = cjOrderId;
        this.cjOrderNum = cjOrderNum;
        this.shipChannel = shipChannel;
        this.deliveryType = deliveryType;
        this.storeId = storeId;
        this.verifyCode = verifyCode;
        this.verifyTime = verifyTime;
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
                        : goods.stream().map(OrderGoodsDtoResponse::fromDomain).collect(Collectors.toList()),
                o.getSource(),
                o.getCjOrderId(),
                o.getCjOrderNum(),
                o.getShipChannel(),
                o.getDeliveryType(),
                o.getStoreId(),
                o.getVerifyCode(),
                o.getVerifyTime());
    }
}
