package org.linlinjava.litemall.wallet.interfaces.dtos.wallet;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallBillAggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BillDtoResponse {

    private Integer billId;
    private String direction;
    private String title;
    private String category;
    private String type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private LocalDateTime addTime;

    public static BillDtoResponse from(LitemallBillAggregate bill) {
        if (bill == null) {
            return null;
        }
        BillDtoResponse dto = new BillDtoResponse();
        dto.setBillId(bill.getBillId() != null ? bill.getBillId().getId() : null);
        dto.setDirection(bill.getDirection() != null ? bill.getDirection().getDisplayName() : null);
        dto.setTitle(bill.getTitle());
        dto.setCategory(bill.getCategory());
        dto.setType(bill.getType());
        dto.setAmount(bill.getAmount() != null ? bill.getAmount().getAmount() : null);
        dto.setBalanceAfter(bill.getBalanceAfter() != null ? bill.getBalanceAfter().getAmount() : null);
        dto.setAddTime(bill.getAddTime());
        return dto;
    }
}
