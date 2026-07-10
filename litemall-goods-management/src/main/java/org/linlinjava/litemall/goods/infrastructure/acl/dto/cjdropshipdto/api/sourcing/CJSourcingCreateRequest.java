package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.sourcing;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request body for CJ {@code product/sourcing/create}. CJ requires {@code productName} and
 * {@code productImage}; everything else is optional. Nulls are omitted from the JSON.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CJSourcingCreateRequest {
    private String thirdProductId;
    private String thirdVariantId;
    private String thirdProductSku;
    private String productName;   // required by CJ
    private String productImage;  // required by CJ
    private String productUrl;
    private String remark;
    private BigDecimal price;     // USD
}
