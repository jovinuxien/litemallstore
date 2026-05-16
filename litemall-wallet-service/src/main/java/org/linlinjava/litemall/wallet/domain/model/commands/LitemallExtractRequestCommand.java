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
public class LitemallExtractRequestCommand {

    private Integer userId;
    private String realName;
    private String extractType;
    private String bankCode;
    private String bankAddress;
    private BigDecimal extractAmount;
}
