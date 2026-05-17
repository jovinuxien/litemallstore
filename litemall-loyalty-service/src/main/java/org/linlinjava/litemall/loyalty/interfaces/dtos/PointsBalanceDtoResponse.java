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
public class PointsBalanceDtoResponse {
    private Integer userId;
    private Integer balance;
    private Integer totalEarned;
}
