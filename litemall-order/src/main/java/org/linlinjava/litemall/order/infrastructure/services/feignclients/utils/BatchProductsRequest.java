package org.linlinjava.litemall.order.infrastructure.services.feignclients.utils;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

@Data
@AllArgsConstructor
public class BatchProductsRequest {
        private Set<Integer> productIds;
}
