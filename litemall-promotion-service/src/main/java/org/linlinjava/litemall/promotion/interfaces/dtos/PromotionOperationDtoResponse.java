package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
public class PromotionOperationDtoResponse {

    private boolean success;
    private String message;
    private String operationType;
    private Map<String, Object> data;
}
