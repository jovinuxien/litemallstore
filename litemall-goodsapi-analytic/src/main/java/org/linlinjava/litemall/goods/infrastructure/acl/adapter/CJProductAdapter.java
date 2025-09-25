package org.linlinjava.litemall.goods.infrastructure.acl.adapter;

import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productdetail.CJProductDetailData;
import org.linlinjava.litemall.goods.infrastructure.acl.model.UnifiedProduct;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class CJProductAdapter implements ProductAdapter {
    @Override
    public List<UnifiedProduct> adapt(List<?> sourceProducts) {
        List<UnifiedProduct> unifiedProducts = new ArrayList<>();

        for(Object sourceProduct : sourceProducts) {
            if(sourceProduct instanceof CJProductDetailData) {
                CJProductDetailData detailData = (CJProductDetailData) sourceProduct;
                UnifiedProduct unifiedProduct = new UnifiedProduct();



                unifiedProduct.setId("CJ_" + detailData.getPid());
                unifiedProduct.setSku(detailData.getProductSku());

                unifiedProduct.setName(detailData.getProductName());
                unifiedProduct.setNameEn(detailData.getProductNameEn());
                unifiedProduct.setDescription(detailData.getDescription());

                unifiedProduct.setProductType(detailData.getProductType());
                unifiedProduct.setPicUrl(detailData.getProductImage());

                unifiedProduct.setRetailPrice(toBigDecimal(detailData.getSellPrice()));

                unifiedProduct.setWeight(stringToDouble(detailData.getProductWeight()));

                unifiedProduct.setSource("cj_dropshipping");
                unifiedProduct.setAddTime(stringToLocalDateTime(detailData.getCreateTime()));
                //unifiedProduct.setExternalData(detailData);
                unifiedProducts.add(unifiedProduct);
            }
        }
        return List.of();
    }

    private BigDecimal toBigDecimal(Double price) {
        return BigDecimal.valueOf(price);
    }

    private Double stringToDouble(String weight) {
        return Double.parseDouble(weight);
    }

    private LocalDateTime stringToLocalDateTime(String date) {
        return LocalDateTime.parse(date);
    }
}
