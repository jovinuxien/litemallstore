package org.linlinjava.litemall.order.interfaces.dtos.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.util.LitemallOrderHandleOption;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A row of the customer "My Orders" list, shaped after the SPA
 * {@code IOrderListItem} model: enough to render the card plus the
 * status-driven action buttons ({@code handleOption}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class OrderListItemDtoResponse {

    private final Integer id;
    private final String orderSn;
    private final BigDecimal actualPrice;
    private final String orderStatusText;
    private final OrderHandleOptionDtoResponse handleOption;
    private final Short aftersaleStatus;
    private final List<OrderGoodsDtoResponse> goodsList;

    public OrderListItemDtoResponse(Integer id, String orderSn, BigDecimal actualPrice,
                                    String orderStatusText, OrderHandleOptionDtoResponse handleOption,
                                    Short aftersaleStatus, List<OrderGoodsDtoResponse> goodsList) {
        this.id = id;
        this.orderSn = orderSn;
        this.actualPrice = actualPrice;
        this.orderStatusText = orderStatusText;
        this.handleOption = handleOption;
        this.aftersaleStatus = aftersaleStatus;
        this.goodsList = goodsList;
    }

    public static OrderListItemDtoResponse fromDomain(LitemallOrderAggregate o,
                                                      List<LitemallOrderGoodsAggregate> goods) {
        return new OrderListItemDtoResponse(
                o.getOrderId() == null ? null : o.getOrderId().getId(),
                o.getOrderSn(),
                o.getActualPrice() == null ? null : o.getActualPrice().getAmount(),
                OrderStatusText.of(o.getOrderStatus()),
                OrderHandleOptionDtoResponse.fromDomain(LitemallOrderHandleOption.forStatus(o.getOrderStatus())),
                o.getAfterSaleStatus() == null ? null : o.getAfterSaleStatus().getCode(),
                goods == null ? List.of()
                        : goods.stream().map(OrderGoodsDtoResponse::fromDomain).collect(Collectors.toList()));
    }
}
