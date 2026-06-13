package org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product;

import com.google.common.util.concurrent.RateLimiter;
import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.api.product.CJProductClient;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.cjcategory.CJCategoryDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProduct;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.utils.CjDropshippingApiUtils;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    @Autowired
    private CJDropshippingConfig config;

    private CJProductDataResponse cachedProducts;
    private CJCategoryDataResponse cachedCategories;
    private long lastProductFetchTime = 0;
    private long lastCategoryFetchTime = 0;
    // Rate limiter - 1 request per second
    private final RateLimiter rateLimiter = RateLimiter.create(1.0); // 1 request per second
    // Paced limiter for the bulk category fetch (indexing path): blocks fetch-pace-seconds between
    // CJ /product/list calls so a multi-category plan stays within the CJ quota. Built lazily from
    // config; the first acquire returns immediately, each subsequent one waits the configured pace.
    private RateLimiter pacedLimiter;


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

    private synchronized RateLimiter pacedLimiter() {
        if (pacedLimiter == null) {
            int pace = Math.max(1, config.getFetchPaceSeconds());
            pacedLimiter = RateLimiter.create(1.0 / pace);
        }
        return pacedLimiter;
    }

    /**
     * Fetch up to {@code targetCount} CJ products from one CJ category, paging at {@code pageSize}
     * and pacing each upstream call by {@code fetch-pace-seconds} (blocking) so the CJ quota is
     * respected. Stops at {@code targetCount}, on category exhaustion, on an empty page, or on the
     * first failed page (returning whatever was gathered so far). Bypasses the 1h list cache — the
     * nightly indexing job wants fresh data and pacing, not the cached single-page blob.
     */
    public List<CJProduct> fetchByCategory(String categoryId, int targetCount, int pageSize) {
        List<CJProduct> acc = new ArrayList<>();
        if (targetCount <= 0) {
            return acc;
        }
        int page = 1;
        while (acc.size() < targetCount) {
            pacedLimiter().acquire(); // blocks ~fetch-pace-seconds between CJ calls
            CJProductDataResponse resp;
            try {
                resp = productClient.getProductList(categoryId, page, pageSize);
            } catch (RuntimeException ex) {
                logger.warn("CJ category {} page {} fetch failed: {}", categoryId, page, ex.getMessage());
                break;
            }
            if (resp == null || resp.getData() == null || resp.getData().getList() == null
                    || resp.getData().getList().isEmpty()) {
                break;
            }
            acc.addAll(resp.getData().getList());
            int total = resp.getData().getTotal();
            if ((long) page * pageSize >= total) {
                break; // upstream exhausted
            }
            page++;
        }
        return acc.size() > targetCount ? new ArrayList<>(acc.subList(0, targetCount)) : acc;
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
