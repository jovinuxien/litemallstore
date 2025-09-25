package org.linlinjava.litemall.goods.domain.model.analysis.datamodel;

import lombok.Getter;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProduct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class TrackedProducts {

    private final List<CJProduct> currentProducts = Collections.synchronizedList(new ArrayList<>());
    private final List<CJProduct> newProducts = Collections.synchronizedList(new ArrayList<>());
    private final List<CJProduct> disappearedProducts = Collections.synchronizedList(new ArrayList<>());

    public void addCurrentProduct(List<CJProduct> products) {
        currentProducts.addAll(products);
    }

    public void addNewProduct(CJProduct product) {
        newProducts.add(product);
    }
    public void addDisappearedProduct(CJProduct product) {
        disappearedProducts.add(product);
    }



}
