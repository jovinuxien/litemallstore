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
public class SignInDtoResponse {
    private boolean success;
    private Integer userId;
    private Integer integralEarned;
    private boolean todayAlreadySigned;
    private Integer totalSignDays;
}
