package org.linlinjava.litemall.loyalty.interfaces.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoyaltyOperationDtoResponse {
    private boolean success;
    private String message;
    private Integer userId;
    private String operationType;
    private Integer newBalance;
    private Integer pointsChanged;
}
