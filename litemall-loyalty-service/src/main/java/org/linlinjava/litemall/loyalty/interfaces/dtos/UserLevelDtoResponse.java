package org.linlinjava.litemall.loyalty.interfaces.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserLevelDtoResponse {
    private Integer userId;
    private Byte grade;
    private String levelName;
    private Integer currentExperience;
    private Integer nextLevelExperience;
    private BigDecimal discount;
}
