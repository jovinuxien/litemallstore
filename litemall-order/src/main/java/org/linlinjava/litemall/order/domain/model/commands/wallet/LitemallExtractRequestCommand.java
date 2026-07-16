package org.linlinjava.litemall.order.domain.model.commands.wallet;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LitemallExtractRequestCommand {

    public static final String SOURCE_WALLET = "wallet";
    public static final String SOURCE_BROKERAGE = "brokerage";

    private Integer userId;
    private String realName;
    private String extractType;
    private String bankCode;
    private String bankAddress;
    private BigDecimal extractAmount;
    /**
     * Which balance funds the withdrawal: {@link #SOURCE_WALLET} (now_money — the
     * pre-Wave-5 behaviour and the default when absent) or {@link #SOURCE_BROKERAGE}
     * (brokerage_price, Wave 5 affiliate earnings).
     */
    private String source;

    /** True when this request draws on the brokerage balance. */
    public boolean isBrokerageSource() {
        return SOURCE_BROKERAGE.equalsIgnoreCase(source);
    }
}
