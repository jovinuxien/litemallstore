package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.stock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * CJ Dropshipping {@code product/stock/queryByVid} response envelope:
 * <pre>{ code, result, message, data:[ { vid, areaId, areaEn, countryCode,
 *   totalInventoryNum, cjInventoryNum, factoryInventoryNum } ], requestId }</pre>
 * {@code data} is one entry per warehouse/region holding the variant; {@code totalInventoryNum}
 * is that warehouse's combined CJ + factory availability.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CjStockQueryResponse {

    private int code;
    private boolean result;
    private String message;
    private String requestId;
    private List<WarehouseStock> data;

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WarehouseStock {
        private String vid;
        private Long areaId;
        private String areaEn;
        private String countryCode;
        private Integer totalInventoryNum;
        private Integer cjInventoryNum;
        private Integer factoryInventoryNum;
    }
}
