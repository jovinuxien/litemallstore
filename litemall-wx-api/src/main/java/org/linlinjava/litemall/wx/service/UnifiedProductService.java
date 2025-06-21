package org.linlinjava.litemall.wx.service;


/*import org.linlinjava.litemall.core.infrastructure.acl.adapter.CJProductAdapter;
import org.linlinjava.litemall.core.infrastructure.acl.client.cjdropshipclient.api.product.CJProductClient;
import org.linlinjava.litemall.core.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.linlinjava.litemall.core.infrastructure.acl.model.UnifiedProduct;*/
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UnifiedProductService {

    /*private final LitemallGoodsService localGoodsService;
    private final CJProductClient cjProductClient;
    private final CJProductAdapter cjProductAdapter;*/

    /*public UnifiedProductService(LitemallGoodsService goodsService, CJProductClient cjProductClient, CJProductAdapter cjProductAdapter) {
        this.localGoodsService = goodsService;
        this.cjProductClient = cjProductClient;
        this.cjProductAdapter = cjProductAdapter;
    }*/

    public Map<String, Object> searchUnifiedProduct(Integer categoryId,
                                                    String keyword, Integer page, Integer limit, String sort, String order) {
    //List<LitemallGoods> localGoods = localGoodsService.querySelective(categoryId, null, keyword, page, limit, sort, order);

    // Search CJ Products (with pagination)
     /*   CJProductDataResponse cjProductDataResponse = cjProductClient.getProductList();
        System.out.println("the cjProductDataResponse: " + cjProductDataResponse);
        List<UnifiedProduct> cjProducts = cjProductAdapter.adapt(cjProductDataResponse.getData().getList());
        System.out.println("This is the cjProducts before combining them: " + cjProducts);*/

        // Combine local and CJ products

      /*  List<UnifiedProduct> allProducts = new ArrayList<>();
       allProducts.addAll(localGoods.stream()
                .map(this::convertLocalToUnified)
                .collect(Collectors.toList()));
        allProducts.addAll(cjProducts);

        // Sort combined results if needed
        if ("price".equals(sort)) {
            allProducts.sort(Comparator.comparing(UnifiedProduct::getRetailPrice));
            if ("desc".equals(order)) {
                Collections.reverse(allProducts);
            }
        } else if ("data".equals(sort) || "addTime".equals(sort) || "createTime".equals(sort)) {
            Comparator<UnifiedProduct> dateComparator =
                    Comparator.comparing(UnifiedProduct::getAddTime, Comparator.nullsLast(Comparator.reverseOrder()));

            if("asc".equals(order)){
                dateComparator = Comparator.comparing(UnifiedProduct::getAddTime, Comparator.nullsFirst(Comparator.naturalOrder()));
            }
            allProducts.sort(dateComparator);
        }

        int total = localGoods.size() + cjProducts.size();
        int fromIndex = (page - 1) * limit;
        int toIndex = Math.min(fromIndex + limit, allProducts.size());
        List<UnifiedProduct> pagedResults = allProducts.subList(fromIndex, toIndex);
*/
        // Prepare response
        Map<String, Object> entity = new HashMap<>();
        /*entity.put("list", pagedResults);
        entity.put("total", total);*/
        entity.put("page", page);
        entity.put("limit", limit);
        //entity.put("pages", (int) Math.ceil((double) total / limit));

        return entity;
    }


    /*private UnifiedProduct convertLocalToUnified(LitemallGoods localProduct) {
        UnifiedProduct unified = new UnifiedProduct();
        unified.setId("LOCAL_" + localProduct.getId());
        unified.setName(localProduct.getName());

        unified.setBrief(localProduct.getBrief());
        unified.setDetail(localProduct.getDetail());
        unified.setKeywords(localProduct.getKeywords());

        unified.setRetailPrice(localProduct.getRetailPrice());
        unified.setCounterPrice(localProduct.getCounterPrice());
        unified.setSuggestedPrice(localProduct.getCounterPrice());

        unified.setPicUrl(localProduct.getPicUrl());

        unified.setAddTime(localProduct.getAddTime());
        unified.setUpdateTime(localProduct.getUpdateTime());

        unified.setOnSale(localProduct.getIsOnSale());
        unified.setHot(localProduct.getIsHot());
        unified.setNew(localProduct.getIsNew());
        // Map other fields...
        unified.setSource("local");
        return unified;
    }*/


}
