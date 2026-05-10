package org.linlinjava.litemall.order.infrastructure.services.feignclients.utils;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

@Data
@AllArgsConstructor
public class BatchGoodsRequest {
    private Set<Integer> goodsIds;
}
