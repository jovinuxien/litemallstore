package org.linlinjava.litemall.core.infrastructure.acl.adapter;

import org.linlinjava.litemall.core.infrastructure.acl.model.UnifiedProduct;

import java.util.List;

public interface ProductAdapter {
    List<UnifiedProduct> adapt(List<?> sourceProducts);
}
