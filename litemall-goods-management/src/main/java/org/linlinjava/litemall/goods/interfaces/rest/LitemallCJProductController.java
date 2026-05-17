package org.linlinjava.litemall.goods.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.linlinjava.litemall.core.util.JacksonUtil;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.cjcategory.CJCategoryDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.authentication.CJAuthenticationRequest;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.authentication.CJAuthenticationResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.CJAuthenticationService;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/srv/cjAuth")
@Validated
public class LitemallCJProductController {

    @Autowired
    private CJProductService productService;
    @Autowired
    private CJAuthenticationService authenticationService;


    @GetMapping("/productCJList")
    public Object getProductList(@RequestParam(required = false) String categoryId){

        var productResponse = productService.fetchProductList();
        Map<String, Object> data = new HashMap<>();
        data.put("code", productResponse.getCode());
        data.put("result", productResponse.isResult());
        data.put("message", productResponse.getMessage());
        data.put("data", productResponse.getData());

        return ResponseUtil.ok(data);

       /* try{
            List<LitemallGoodsAggregate> products;
            if (categoryId != null) {
                products = productService.cjFilterProductByCategory(categoryId);
            } else {
                CJProductDataResponse response = productService.fetchProductList();
                products = productService.convertProducts(response);
            }
            //productService.cjFilterProductByCategory();
            Map<String, Object> data = new HashMap<>();
           *//* data.put("code", productListResponse.getCode());
            data.put("result", productListResponse.isResult());
            data.put("message", productListResponse.getMessage());*//*
            data.put("data", products);
            return ResponseUtil.ok(data);
        }catch (Exception e){
            return ResponseUtil.fail();
        }*/
    }



    @GetMapping("/productCJDetail")
    public Object getProductDetail(@RequestParam long productId){

        /*CJProductDetailResponse productDetailResponse = productService.fetchProductById(productId);
        Map<String, Object> data = new HashMap<>();
        data.put("code", productDetailResponse.getCode());
        data.put("result", productDetailResponse.isResult());
        data.put("message", productDetailResponse.getMessage());
        data.put("data", productDetailResponse.getData());*/

        //return ResponseUtil.ok(data);
        return ResponseUtil.ok();
    }

    @GetMapping("/cjCategoryList")
    public Object getCjCategories(){
        CJCategoryDataResponse  categoryDataResponse = productService.fetchCategoryList();
        Map<String, Object> data = new HashMap<>();
        data.put("code", categoryDataResponse.getCode());
        data.put("result", categoryDataResponse.isResult());
        data.put("message", categoryDataResponse.getMessage());
        data.put("data", categoryDataResponse.getData());
        return ResponseUtil.ok(data);
    }

    @PostMapping(value = "/accessToken")
    public Object cjAccessToken(@RequestBody String body, HttpServletRequest request) {

        String email = JacksonUtil.parseString(body, "email");
        String cjApiKey = JacksonUtil.parseString(body, "cjApiKey");

        var cjRequest = new CJAuthenticationRequest(email, cjApiKey);
        Object error = validate(cjRequest);

        if(error != null){
            return error;
        }

        try{
            authenticationService.accessTokenFromAuthentication(cjRequest.getEmail(), cjRequest.getCjApiKey());
            CJAuthenticationResponse cjResponse = authenticationService.getResponseAuthAccessData(cjRequest.getEmail());
            Map<String, Object> data = new HashMap<>();
            data.put("joviInfo", cjRequest);
            data.put("cachedResponse", cjResponse);
            return ResponseUtil.ok(data);
        }catch (Exception e){
            e.printStackTrace();
            System.err.println("Detailed error: " + e.getMessage());
            return ResponseUtil.fail(500, "Internal Server Error from client request");
        }

    }

    private Object validate(CJAuthenticationRequest request){

        String email =  request.getEmail();
        if(StringUtils.isEmpty(email)){
            return ResponseUtil.badArgument();
        }
        String cjApiKey = request.getCjApiKey();
        if(StringUtils.isEmpty(cjApiKey)){
            return ResponseUtil.badArgument();
        }
        return null;
    }
}
