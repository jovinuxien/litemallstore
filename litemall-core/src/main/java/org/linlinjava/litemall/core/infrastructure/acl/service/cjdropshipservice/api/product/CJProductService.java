package org.linlinjava.litemall.core.infrastructure.acl.service.cjdropshipservice.api.product;

import org.checkerframework.checker.units.qual.A;
import org.linlinjava.litemall.core.config.CJDropshippingConfig;
import org.linlinjava.litemall.core.infrastructure.acl.client.cjdropshipclient.api.product.CJProductClient;
import org.linlinjava.litemall.core.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.linlinjava.litemall.core.infrastructure.acl.dto.cjdropshipdto.api.productdetail.CJProductDetailResponse;
import org.linlinjava.litemall.core.infrastructure.acl.service.cjdropshipservice.CJAuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CJProductService {


    @Autowired
    private CJProductClient productClient;



    public CJProductDataResponse fetchProductList(){
        return productClient.getProductList();
    }

    public CJProductDetailResponse fetchProductById(long productId){
        return productClient.getProductById(productId);
    }
}
