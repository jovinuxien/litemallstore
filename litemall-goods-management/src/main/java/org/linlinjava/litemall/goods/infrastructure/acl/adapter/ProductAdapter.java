package org.linlinjava.litemall.goods.infrastructure.acl.adapter;

import org.linlinjava.litemall.goods.infrastructure.acl.model.UnifiedProduct;

import java.util.List;

public interface ProductAdapter {
    List<UnifiedProduct> adapt(List<?> sourceProducts);
}
