package org.linlinjava.litemall.wallet.domain.model.commands;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LitemallWalletDebitCommand {

    private Integer userId;
    private BigDecimal amount;
    private String title;
    private String category;
    private String type;
    private String linkId;
    private String mark;
}
