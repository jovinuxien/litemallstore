package org.linlinjava.litemall.wx.web;


import org.linlinjava.litemall.core.infrastructure.acl.client.cjdropshipclient.api.product.CJProductClient;
import org.linlinjava.litemall.core.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.linlinjava.litemall.core.infrastructure.acl.dto.cjdropshipdto.api.productdetail.CJProductDetailResponse;
import org.linlinjava.litemall.core.infrastructure.acl.service.cjdropshipservice.CJAuthenticationService;
import org.linlinjava.litemall.core.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/wx/cjdropship/product")
@Validated
public class CJProductController {
    @Autowired
    private CJProductService productService;

    @GetMapping("/productCJList")
    public Object getProductList(){
        CJProductDataResponse productListResponse = productService.fetchProductList();
        Map<String, Object> data = new HashMap<>();
        data.put("code", productListResponse.getCode());
        data.put("result", productListResponse.isResult());
        data.put("message", productListResponse.getMessage());
        data.put("data", productListResponse.getData());

        return ResponseUtil.ok(data);
    }

    @GetMapping("/productCJDetail")
    public Object getProductDetail(@RequestParam long productId){

        CJProductDetailResponse productDetailResponse = productService.fetchProductById(productId);
        Map<String, Object> data = new HashMap<>();
        data.put("code", productDetailResponse.getCode());
        data.put("result", productDetailResponse.isResult());
        data.put("message", productDetailResponse.getMessage());
        data.put("data", productDetailResponse.getData());

        return ResponseUtil.ok(data);
    }
}
