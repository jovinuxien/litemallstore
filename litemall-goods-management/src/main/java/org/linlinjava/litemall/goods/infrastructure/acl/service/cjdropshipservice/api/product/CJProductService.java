package org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product;

import com.google.common.util.concurrent.RateLimiter;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.api.product.CJProductClient;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.cjcategory.CJCategoryDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProduct;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.utils.CjDropshippingApiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static org.linlinjava.litemall.db.domain.LitemallGoods.Column.categoryId;

@Service
public class CJProductService {

    private static final Logger logger = LoggerFactory.getLogger(CJProductService.class);

    @Autowired
    private CJProductClient productClient;
    @Autowired
    private CjDropshippingApiUtils apiUtils;

    private CJProductDataResponse cachedProducts;
    private CJCategoryDataResponse cachedCategories;
    private long lastProductFetchTime = 0;
    private long lastCategoryFetchTime = 0;
    // Rate limiter - 1 request per second
    private final RateLimiter rateLimiter = RateLimiter.create(1.0); // 1 request per second


    public synchronized CJProductDataResponse fetchProductList(){
        //return productClient.getProductList();
        // Check if we have recent cached data (e.g., within last hour)
        if (cachedProducts != null && System.currentTimeMillis() - lastProductFetchTime < 3600000) {
            return cachedProducts;
        }

        // Wait for rate limiter permit
        rateLimiter.acquire();

        try {
            CJProductDataResponse response = productClient.getProductList();
            cachedProducts = response;
            lastProductFetchTime = System.currentTimeMillis();
            return response;
        } catch (Exception e) {
            logger.error("Failed to fetch products", e);
            throw e;
        }
    }



    public synchronized CJCategoryDataResponse fetchCategoryList(){
        if (cachedCategories != null && System.currentTimeMillis() - lastCategoryFetchTime < 86400000) {
            return cachedCategories; // Categories change less often, cache longer
        }

        rateLimiter.acquire();

        try {
            CJCategoryDataResponse response = productClient.getCategoryList();
            cachedCategories = response;
            lastCategoryFetchTime = System.currentTimeMillis();
            return response;
        } catch (Exception e) {
            logger.error("Failed to fetch categories", e);
            throw e;
        }
    }

    //public void cjFilterProductByCategory(String categoryId){
    //public void cjFilterProductByCategory(String categoryId){
    public List<LitemallGoodsAggregate> cjFilterProductByCategory(String categoryId){
        CJProductDataResponse productDataResponse = this.fetchProductList();
        CJCategoryDataResponse categoryDataResponse = this.fetchCategoryList();

        /*List<CJProduct> productsInCategory = CjDropshippingApiUtils.getProductByCategory(
                productDataResponse.getData().getList(),
                "1E4A1FD7-738C-4AEF-9793-BDE062158BD6" // Belts & Cummerbunds
        );

        List<LitemallCategoryAggregate> litemallCategories = apiUtils.cjCategoryToLitemallCategory(categoryDataResponse);

        List<LitemallGoodsAggregate> litemallGoods = apiUtils.convertProducts(productDataResponse);

        List<LitemallGoodsAggregate> filteredLitemallGoods = productsInCategory.stream()
                .map(cjProduct -> {
                   return  apiUtils.convertProduct(cjProduct);
                })
                .toList();

        System.out.println("the filtered goods are: " + filteredLitemallGoods);*/

        // Process data
        List<CJProduct> productsInCategory = CjDropshippingApiUtils.getProductByCategory(
                productDataResponse.getData().getList(),
                categoryId
        );

        List<LitemallCategoryAggregate> litemallCategories = apiUtils.cjCategoryToLitemallCategory(categoryDataResponse);

        return productsInCategory.stream()
                .map(apiUtils::convertProduct)
                .toList();
    }

    public  List<LitemallGoodsAggregate> convertProducts(CJProductDataResponse productData) {
        return productData.getData().getList().stream()
                .map(apiUtils::convertProduct)
                .collect(Collectors.toList());
    }
}
