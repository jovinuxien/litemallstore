package org.linlinjava.litemall.order.infrastructure.services.feignclients.utils;

import lombok.Data;

import java.util.List;

@Data
public class BatchStockReduceRequest {
        private List<StockReduceItem> reduceItems;
        // constructors, getters, setters

        public static class StockReduceItem {
            private Integer productId;
            private Integer reduceQuantity;
            // constructors, getters, setters
        }
}
