package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One CJ order line: the CJ variant id ({@code vid}) and quantity, plus the OPTIONAL
 * createOrderV2 store-side facts CJ documents — {@code unitPrice} ("item pricing", the
 * price the line was sold at) and {@code storeLineItemId} ("lineItemId of your store
 * order"). Both are {@code NON_NULL}: a line whose price is unknown OMITS the field — a
 * {@code 0} would be a false declaration, not a missing one.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CjOrderProduct {
    @JsonProperty("vid")
    private String vid;
    @JsonProperty("quantity")
    private int quantity;
    /** Unit price the line was sold at, in the currency the facade declares (optional). */
    @JsonProperty("unitPrice")
    private BigDecimal unitPrice;
    /** Our {@code litemall_order_goods.id} for reconciliation in the CJ dashboard (optional). */
    @JsonProperty("storeLineItemId")
    private String storeLineItemId;

    /** Legacy two-arg form (vid + quantity) — freight quotes and the manual controller path. */
    public CjOrderProduct(String vid, int quantity) {
        this(vid, quantity, null, null);
    }
}
