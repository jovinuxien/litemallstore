package org.linlinjava.litemall.order.interfaces.dtos.order;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.linlinjava.litemall.order.domain.model.util.LitemallOrderHandleOption;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class OrderHandleOptionDtoResponse {

    private final boolean cancel;
    private final boolean delete;
    private final boolean pay;
    private final boolean refund;
    private final boolean confirm;
    private final boolean comment;
    private final boolean rebuy;
    private final boolean aftersale;

    public OrderHandleOptionDtoResponse(boolean cancel, boolean delete, boolean pay,
                                        boolean refund, boolean confirm, boolean comment,
                                        boolean rebuy, boolean aftersale) {
        this.cancel = cancel;
        this.delete = delete;
        this.pay = pay;
        this.refund = refund;
        this.confirm = confirm;
        this.comment = comment;
        this.rebuy = rebuy;
        this.aftersale = aftersale;
    }

    public static OrderHandleOptionDtoResponse fromDomain(LitemallOrderHandleOption domainOption) {
        return new OrderHandleOptionDtoResponse(
                domainOption.isCancel(),
                domainOption.isDelete(),
                domainOption.isPay(),
                domainOption.isRefund(),
                domainOption.isConfirm(),
                domainOption.isComment(),
                domainOption.isRebuy(),
                domainOption.isAftersale()
        );
    }
}
