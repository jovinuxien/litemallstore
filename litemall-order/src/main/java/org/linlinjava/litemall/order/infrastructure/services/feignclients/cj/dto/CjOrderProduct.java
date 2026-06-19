package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One CJ order line: the CJ variant id ({@code vid}) and quantity. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CjOrderProduct {
    @JsonProperty("vid")
    private String vid;
    @JsonProperty("quantity")
    private int quantity;
}
