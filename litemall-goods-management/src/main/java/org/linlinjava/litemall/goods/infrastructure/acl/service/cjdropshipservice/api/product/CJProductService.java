package org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product;

import com.google.common.util.concurrent.RateLimiter;
import org.linlinjava.litemall.goods.infrastructure.acl.cache.CjRawCacheRepository;
import org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.api.product.CJProductClient;
import org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.api.product.CJProductInventoryClient;
import org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.api.product.CJProductReviewClient;
import org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.api.product.CJProductVideoClient;
import org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.api.sourcing.CJSourcingClient;
import org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.api.warehouse.CJWarehouseClient;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productvideo.CJProductVideo;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productvideo.CJProductVideoResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.sourcing.CJSourcingCreateRequest;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.sourcing.CJSourcingCreateResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.sourcing.CJSourcingQueryItem;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.sourcing.CJSourcingQueryResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.warehouse.CJWarehouseDetailResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productreview.CJProductReviewData;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productreview.CJProductReviewDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.cjcategory.CJCategoryDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.inventory.CJInventoryData;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.inventory.CJInventoryDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProduct;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productdetail.CJProductDetailData;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productdetail.CJProductDetailResponse;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CJProductService {

    private static final Logger logger = LoggerFactory.getLogger(CJProductService.class);

    @Autowired
    private CJProductClient productClient;
    @Autowired
    private CJProductInventoryClient inventoryClient;
    @Autowired
    private CJProductReviewClient reviewClient;
    @Autowired
    private CJProductVideoClient videoClient;
    @Autowired
    private CJSourcingClient sourcingClient;
    @Autowired
    private CJWarehouseClient warehouseClient;
    @Autowired
    private CJDropshippingConfig config;
    // Durable staging buffer for raw CJ payloads (replaces the old per-instance in-memory caches:
    // cachedProducts / cachedCategories / detailCache). TTL is owned by Redis (config raw-ttl-seconds).
    @Autowired
    private CjRawCacheRepository rawCache;

    // Rate limiter - 1 request per second
    private final RateLimiter rateLimiter = RateLimiter.create(1.0); // 1 request per second
    // Paced limiter for the bulk category fetch (indexing path): blocks fetch-pace-seconds between
    // CJ /product/list calls so a multi-category plan stays within the CJ quota. Built lazily from
    // config; the first acquire returns immediately, each subsequent one waits the configured pace.
    private RateLimiter pacedLimiter;


    public synchronized CJProductDataResponse fetchProductList(){
        // Serve from the Redis staging buffer when present (survives restart, spares the CJ quota).
        Optional<CJProductDataResponse> cached =
                rawCache.get(CjRawCacheRepository.defaultListKey(), CJProductDataResponse.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        // Wait for rate limiter permit
        rateLimiter.acquire();

        try {
            CJProductDataResponse response = productClient.getProductList();
            rawCache.put(CjRawCacheRepository.defaultListKey(), response);
            return response;
        } catch (Exception e) {
            logger.error("Failed to fetch products", e);
            throw e;
        }
    }



    public synchronized CJCategoryDataResponse fetchCategoryList(){
        Optional<CJCategoryDataResponse> cached =
                rawCache.get(CjRawCacheRepository.categoriesKey(), CJCategoryDataResponse.class);
        if (cached.isPresent()) {
            return cached.get(); // Categories change less often; the raw TTL covers them too.
        }

        rateLimiter.acquire();

        try {
            CJCategoryDataResponse response = productClient.getCategoryList();
            rawCache.put(CjRawCacheRepository.categoriesKey(), response);
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
     * Fetch up to {@code targetCount} CJ products from one CJ category, paging at {@code pageSize}.
     * Each page is read THROUGH the Redis staging buffer: a cached page is reused directly (no
     * pacing, no API call); a miss triggers a paced upstream call (blocking {@code fetch-pace-seconds})
     * whose raw response is then cached, so a re-index within the raw TTL never re-hits the CJ quota.
     * Stops at {@code targetCount}, on category exhaustion, on an empty page, or on the first failed
     * page (returning whatever was gathered so far).
     */
    public List<CJProduct> fetchByCategory(String categoryId, int targetCount, int pageSize) {
        List<CJProduct> acc = new ArrayList<>();
        if (targetCount <= 0) {
            return acc;
        }
        int page = 1;
        while (acc.size() < targetCount) {
            String key = CjRawCacheRepository.listKey(categoryId, page);
            CJProductDataResponse resp = rawCache.get(key, CJProductDataResponse.class).orElse(null);
            if (resp == null) {
                pacedLimiter().acquire(); // blocks ~fetch-pace-seconds between live CJ calls
                try {
                    resp = productClient.getProductList(categoryId, page, pageSize);
                } catch (RuntimeException ex) {
                    logger.warn("CJ category {} page {} fetch failed: {}", categoryId, page, ex.getMessage());
                    break;
                }
                if (resp != null) {
                    rawCache.put(key, resp);
                }
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

    /**
     * Fetch one CJ product's full detail by raw UUID {@code pid}, memoized in the Redis staging buffer.
     * Returns {@code null} if CJ has no such product (so the caller can surface a clean not-found
     * rather than throwing).
     */
    public CJProductDetailData getProductDetail(String pid) {
        if (pid == null || pid.isBlank()) {
            return null;
        }
        String key = CjRawCacheRepository.detailKey(pid);
        Optional<CJProductDetailData> cached = rawCache.get(key, CJProductDetailData.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        rateLimiter.acquire();
        CJProductDetailResponse response = productClient.getProductDetail(pid);
        CJProductDetailData data = response != null ? response.getData() : null;
        if (data != null) {
            rawCache.put(key, data);
        }
        return data;
    }

    /**
     * Fetch one CJ variant's warehouse inventory by {@code vid}, memoized in the Redis staging buffer
     * (so a re-enrich within the raw TTL never re-hits the CJ quota). Returns the per-area stock list
     * (empty on a miss / error), which the caller sums to a single SKU stock figure. CJ inventory is
     * per-variant, so this is one call per SKU — the enrichment job batches + paces these.
     */
    public List<CJInventoryData> getInventory(String vid) {
        if (vid == null || vid.isBlank()) {
            return List.of();
        }
        String key = CjRawCacheRepository.inventoryKey(vid);
        Optional<CJInventoryDataResponse> cached = rawCache.get(key, CJInventoryDataResponse.class);
        if (cached.isPresent()) {
            return cached.get().getData() != null ? cached.get().getData() : List.of();
        }
        // CJ enforces a hard 1-request/second global QPS; on a 429 ("Too Many Requests") back off and
        // retry once so a transient burst doesn't drop the variant to fallback stock.
        for (int attempt = 0; attempt < 2; attempt++) {
            rateLimiter.acquire();
            try {
                CJInventoryDataResponse response = inventoryClient.queryByVid(vid);
                if (response != null && response.getData() != null) {
                    rawCache.put(key, response);
                    return response.getData();
                }
                return List.of();
            } catch (RuntimeException ex) {
                boolean rateLimited = ex.getMessage() != null
                        && (ex.getMessage().contains("429") || ex.getMessage().contains("Too Many Requests"));
                if (rateLimited && attempt == 0) {
                    try {
                        Thread.sleep(1200);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue; // retry once after backing off past the 1s window
                }
                logger.warn("CJ inventory fetch failed for vid {}: {}", vid, ex.getMessage());
                break;
            }
        }
        return List.of();
    }

    /**
     * Fetch one page of a CJ product's customer reviews by raw UUID {@code pid}, memoized in the
     * Redis staging buffer (raw TTL, so a product page reload never re-hits the CJ quota). Returns
     * {@code null} on any failure so the caller can degrade to an empty review list — a CJ outage
     * must never break the product page.
     */
    public CJProductReviewData getProductComments(String pid, int pageNum, int pageSize) {
        if (pid == null || pid.isBlank()) {
            return null;
        }
        String key = CjRawCacheRepository.reviewsKey(pid, pageNum, pageSize);
        Optional<CJProductReviewDataResponse> cached = rawCache.get(key, CJProductReviewDataResponse.class);
        if (cached.isPresent()) {
            return cached.get().getData();
        }
        // Same hard 1-request/second global CJ QPS as inventory: back off and retry once on a 429.
        for (int attempt = 0; attempt < 2; attempt++) {
            rateLimiter.acquire();
            try {
                CJProductReviewDataResponse response = reviewClient.getProductComments(pid, pageNum, pageSize);
                if (response != null && response.getData() != null) {
                    rawCache.put(key, response);
                    return response.getData();
                }
                return null;
            } catch (RuntimeException ex) {
                boolean rateLimited = ex.getMessage() != null
                        && (ex.getMessage().contains("429") || ex.getMessage().contains("Too Many Requests"));
                if (rateLimited && attempt == 0) {
                    try {
                        Thread.sleep(1200);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                logger.warn("CJ product comments fetch failed for pid {}: {}", pid, ex.getMessage());
                break;
            }
        }
        return null;
    }

    /**
     * Fetch a CJ product's video list by raw UUID {@code pid}, memoized in the Redis staging
     * buffer (raw TTL — a product-page reload never re-hits the CJ quota). Returns an empty list
     * for a product with no videos AND on any failure, so the caller can render "no videos"
     * without a CJ outage ever breaking the read path. A products-without-videos response is
     * cached too (it's a valid answer, not a miss).
     */
    public List<CJProductVideo> getProductVideos(String pid) {
        if (pid == null || pid.isBlank()) {
            return List.of();
        }
        String key = CjRawCacheRepository.videosKey(pid);
        Optional<CJProductVideoResponse> cached = rawCache.get(key, CJProductVideoResponse.class);
        if (cached.isPresent()) {
            return cached.get().getData() != null ? cached.get().getData() : List.of();
        }
        // Same hard 1-request/second global CJ QPS: back off and retry once on a 429.
        for (int attempt = 0; attempt < 2; attempt++) {
            rateLimiter.acquire();
            try {
                CJProductVideoResponse response = videoClient.queryVideosByProductId(pid);
                if (response != null && response.isOk()) {
                    rawCache.put(key, response);
                    return response.getData() != null ? response.getData() : List.of();
                }
                return List.of();
            } catch (RuntimeException ex) {
                boolean rateLimited = ex.getMessage() != null
                        && (ex.getMessage().contains("429") || ex.getMessage().contains("Too Many Requests"));
                if (rateLimited && attempt == 0) {
                    try {
                        Thread.sleep(1200);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                logger.warn("CJ product videos fetch failed for pid {}: {}", pid, ex.getMessage());
                break;
            }
        }
        return List.of();
    }

    /**
     * Create a product-sourcing request at CJ. NOT cached (a mutation) but still paced through
     * the shared limiter. Failures propagate as {@link RuntimeException} so the admin surface can
     * show a real errmsg — a silent null here would look like a lost request.
     */
    public CJSourcingCreateResponse createSourcing(CJSourcingCreateRequest request) {
        rateLimiter.acquire();
        return sourcingClient.createSourcing(request);
    }

    /**
     * Query sourcing status for a batch of CJ sourceIds. NOT cached (the status is what's being
     * refreshed); paced through the shared limiter. Returns an empty list on any failure — the
     * refresh is advisory, the local projection stays authoritative for the admin list.
     */
    public List<CJSourcingQueryItem> querySourcing(List<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return List.of();
        }
        rateLimiter.acquire();
        try {
            CJSourcingQueryResponse response = sourcingClient.querySourcing(sourceIds);
            if (response != null && response.isOk() && response.getData() != null) {
                return response.getData();
            }
            return List.of();
        } catch (RuntimeException ex) {
            logger.warn("CJ sourcing query failed for {} sourceIds: {}", sourceIds.size(), ex.getMessage());
            return List.of();
        }
    }

    /**
     * Fetch a CJ warehouse's storage info by {@code storageId}, memoized in the Redis staging
     * buffer (warehouses barely change; the raw TTL is plenty). Returns the full envelope so the
     * caller can distinguish a CJ-side miss (e.g. 1608001 "Warehouse info not found") from a
     * transport failure ({@code null}).
     */
    public CJWarehouseDetailResponse getWarehouseDetail(String storageId) {
        if (storageId == null || storageId.isBlank()) {
            return null;
        }
        String key = CjRawCacheRepository.warehouseKey(storageId);
        Optional<CJWarehouseDetailResponse> cached = rawCache.get(key, CJWarehouseDetailResponse.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        rateLimiter.acquire();
        try {
            CJWarehouseDetailResponse response = warehouseClient.getWarehouseDetail(storageId);
            if (response != null && response.isOk() && response.getData() != null) {
                rawCache.put(key, response); // only cache hits; a CJ-side miss/outage stays retryable
            }
            return response;
        } catch (RuntimeException ex) {
            logger.warn("CJ warehouse detail fetch failed for storageId {}: {}", storageId, ex.getMessage());
            return null;
        }
    }
}
