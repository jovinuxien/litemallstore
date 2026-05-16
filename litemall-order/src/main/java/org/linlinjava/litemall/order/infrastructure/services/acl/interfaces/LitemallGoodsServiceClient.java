package org.linlinjava.litemall.order.infrastructure.services.acl.interfaces;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


public interface LitemallGoodsServiceClient {
    @GetMapping("/api/goods/{productId}")  // Use the same API as the one in the GoodsApi interface
    public Object getProduct(@PathVariable("productId") Integer productId);
}
