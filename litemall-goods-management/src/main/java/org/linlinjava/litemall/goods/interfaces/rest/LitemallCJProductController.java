package org.linlinjava.litemall.goods.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.linlinjava.litemall.core.util.JacksonUtil;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.cjcategory.CJCategoryDataResponse;
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
    @Autowired
    private LitemallCjProductService cjProductStore;


    /**
     * Paginated CJ product list, served from the durable {@code litemall_cj_product} snapshot in the
     * DB — NOT a live CJ API call. Paging through this surface only walks the DB (offset/limit), so
     * navigating pages never re-hits the rate-limited (1-request/300s) CJ API; the snapshot is kept
     * fresh out-of-band by the daily {@code CjCatalogRefreshTask} / {@code POST /srv/private/admin/search/cj-sync}.
     */
    @GetMapping("/productCJList")
    public Object getProductList(@RequestParam(defaultValue = "1") Integer page,
                                 @RequestParam(defaultValue = "20") Integer size) {
        List<LitemallCjProduct> rows = cjProductStore.queryLivePaged(page, size);
        int total = cjProductStore.countLive();
        int totalPages = size <= 0 ? 0 : (total + size - 1) / size;

        Map<String, Object> data = new HashMap<>();
        data.put("list", rows);
        data.put("total", total);
        data.put("page", page);
        data.put("limit", size);
        data.put("totalPages", totalPages);
        return ResponseUtil.ok(data);
    }



    /**
     * Raw CJ product detail by UUID {@code pid} (CJ pids are UUID Strings, never longs). The
     * customer detail page is served by {@code GET /srv/goods/detail?id=cj_<pid>}; this endpoint
     * exposes the unmapped CJ payload for debugging/admin inspection.
     */
    @GetMapping("/productCJDetail")
    public Object getProductDetail(@RequestParam String pid){
        var detail = productService.getProductDetail(pid);
        if (detail == null) {
            return ResponseUtil.badArgumentValue();
        }
        Map<String, Object> data = new HashMap<>();
        data.put("data", detail);
        return ResponseUtil.ok(data);
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
