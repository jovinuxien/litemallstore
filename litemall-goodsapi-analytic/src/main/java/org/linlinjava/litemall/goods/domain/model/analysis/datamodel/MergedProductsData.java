package org.linlinjava.litemall.goods.domain.model.analysis.datamodel;

import lombok.Getter;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProduct;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MergedProductsData {

    private final Map<String, List<CJProduct>> productByFile = new ConcurrentHashMap<>();
    private final Set<CJProduct> allProducts = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, Object> responseMetadata = new ConcurrentHashMap<>();


    @Getter
    private LocalDateTime latestTimestamp = LocalDateTime.MIN;

    public void addProducts(String fileName, List<CJProduct> products, CJProductDataResponse response) {
        productByFile.put(fileName, products);
        allProducts.addAll(products);
        latestTimestamp = LocalDateTime.now();

        // Store response metadata
        if (response != null) {
            responseMetadata.put(fileName, Map.of(
                    "code", response.getCode(),
                    "message", response.getMessage(),
                    "result", response.isResult()
            ));
        }
    }

    public List<CJProduct> getAllProducts() {
        return new ArrayList<>(allProducts);
    }

    public Set<String> getSourceFiles() {
        return productByFile.keySet();
    }

}
