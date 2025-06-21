package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class CJProductData {
    @JsonProperty("pageNum")
    private int pageNum; // Current page number, e.g., 1
    @JsonProperty("pageSize")
    private int pageSize; // Number of items per page, e.g., 20
    @JsonProperty("total")
    private int total; // Total number of items, e.g., 1
    @JsonProperty("list")
    private List<CJProduct> list; // List of products
}
