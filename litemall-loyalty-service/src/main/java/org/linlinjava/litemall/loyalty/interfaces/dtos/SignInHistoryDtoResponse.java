package org.linlinjava.litemall.loyalty.interfaces.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SignInHistoryDtoResponse {
    private Integer signId;
    private Integer integral;
    private LocalDateTime signDate;
}
