package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One dispute line as CJ's dispute endpoints exchange it ({@code productInfoList} entry
 * of disputeConfirmInfo/create): the CJ line-item reference plus quantity and USD price.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CjDisputeLine {
    @JsonProperty("lineItemId")
    private String lineItemId;
    @JsonProperty("quantity")
    private Integer quantity;
    @JsonProperty("price")
    private BigDecimal price;
}
