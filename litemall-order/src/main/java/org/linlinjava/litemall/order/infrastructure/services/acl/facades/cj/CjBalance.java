package org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/** CJ account balance ({@code shopping/pay/getBalance}) — surfaced on the admin order surface. */
@Data
@AllArgsConstructor
public class CjBalance {
    /** Available balance (USD). */
    private BigDecimal amount;
    /** Bonus funds (non-withdrawable). */
    private BigDecimal noWithdrawalAmount;
    /** Frozen/locked funds. */
    private BigDecimal freezeAmount;
}
