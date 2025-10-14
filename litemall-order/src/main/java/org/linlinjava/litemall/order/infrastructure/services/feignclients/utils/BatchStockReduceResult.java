package org.linlinjava.litemall.order.infrastructure.services.feignclients.utils;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class BatchStockReduceResult {
     private Map<Integer, Boolean> reduceResults; // productId -> success
     private List<Integer> failedProductIds;
        // constructors, getters, setters
}
