package org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.util.concurrent.RateLimiter;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.api.product.CJProductClient;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.cjcategory.CJCategoryDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProduct;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductData;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.RateLimitService;
import org.linlinjava.litemall.goods.infrastructure.acl.utils.CjDropshippingApiUtils;
import org.linlinjava.litemall.goods.infrastructure.acl.utils.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.linlinjava.litemall.db.domain.LitemallGoods.Column.categoryId;

@Service
public class CJProductService {

    private static final Logger logger = LoggerFactory.getLogger(CJProductService.class);

    @Value("${cache.directory:/data/product-cache}")
    private String cacheDirectory;

    @Autowired
    private CJProductClient productClient;
    @Autowired
    private CjDropshippingApiUtils apiUtils;
    @Autowired
    private RateLimitService rateLimitService;

    //@Autowired
    //@Qualifier("productDataChannel")
    //private MessageChannel productDataChannel;


    private CJProductDataResponse cachedProducts;
    private CJCategoryDataResponse cachedCategories;

    // Cache products by category ID
    private final  Map<String, CJProductDataResponse> categoryProductCache = new ConcurrentHashMap<>();
    private final  Map<String, Long> categoryFetchTime = new ConcurrentHashMap<>();
    private final  Map<String, Boolean> categoryFetchInProgress = new ConcurrentHashMap<>();
    private final  Queue<String> categoryFetchQueue = new ConcurrentLinkedQueue<>();

    // Cache for categories
    private long lastCategoryFetchTime = 0;
    private long lastProductFetchTime = 0;

    private final RateLimiter rateLimiter = RateLimiter.create(1.0); // 1 request per second
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Cache duration (1 hour for products, 24 hours for categories)
    private static final long PRODUCT_CACHE_DURATION = 3600000; // 1 hour
    private static final long CATEGORY_CACHE_DURATION = 86400000; // 24 hours


    /**
     * This method has some parts of checking too
     */
    @PostConstruct
    public void init(){
       initializeCacheDirectory();
       loadCacheFromFiles();
       //scheduler.scheduleAtFixedRate(this::processFetchQueue, 5, 2, TimeUnit.SECONDS);
       scheduler.scheduleAtFixedRate(this::processFetchQueue, 5, 10, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void cleanup() {
        scheduler.shutdown();
    }



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
        if (cachedCategories != null && System.currentTimeMillis() - lastCategoryFetchTime < CATEGORY_CACHE_DURATION) {
            return cachedCategories; // Categories change less often, cache longer
        }
        rateLimiter.acquire();
        try {
            CJCategoryDataResponse response = productClient.getCategoryList();
            System.out.println("the categoryResponse are: " + response);
            cachedCategories = response;
            lastCategoryFetchTime = System.currentTimeMillis();
            return response;
        } catch (Exception e) {
            logger.error("Failed to fetch categories", e);
            throw e;
        }
    }

    /**
     *
     * @param categoryFirstNames
     * @param categorySecondNames
     * @return
     */
    public synchronized CJProductDataResponse getProductsByTopCategoryNames(
            List<String> categoryFirstNames, List<String> categorySecondNames) {


        CJCategoryDataResponse categoryDataResponse = fetchCategoryList();
        CJProductDataResponse finalResponse = new CJProductDataResponse();
        List<CJProduct> allProducts = new ArrayList<>();


        if (categoryDataResponse != null && categoryDataResponse.isResult() && categoryDataResponse.getData() != null) {
            // Extract all third-level categories and sort by name
            //List<CJCategoryDataResponse.CategoryThird> allThirdCategories = new ArrayList<>();
            List<CJCategoryDataResponse.CategoryThird> selectedThirdCategories = new ArrayList<>();

            for (CJCategoryDataResponse.CategoryData categoryData : categoryDataResponse.getData()) {
                if (categoryData != null && categoryData.getCategoryFirstList() != null &&
                        (categoryFirstNames == null || categoryFirstNames.contains(categoryData.getCategoryFirstName()))) {

                    for (CJCategoryDataResponse.CategorySecond categorySecond : categoryData.getCategoryFirstList()) {
                        if (categorySecond != null && categorySecond.getCategorySecondList() != null &&
                                (categorySecondNames == null || categorySecondNames.contains(categorySecond.getCategorySecondName()))) {

                            selectedThirdCategories.addAll(categorySecond.getCategorySecondList());
                        }
                    }
                }
            }
            logger.info("Found {} third-level categories from selected criteria", selectedThirdCategories.size());

            // Fetch products for each selected category with retry logic
            //logger.info("Fetching products for top {} categories", topCategories.size());
            for (CJCategoryDataResponse.CategoryThird category : selectedThirdCategories) {
                CJProductDataResponse categoryResponse = fetchCategoryProductsWithRetry(category.getCategoryId());
                if (categoryResponse != null && categoryResponse.isResult() && categoryResponse.getData() != null) {
                    allProducts.addAll(categoryResponse.getData().getList());
                }
            }
        }

        // Build final response
        CJProductData data = new CJProductData();
        data.setList(allProducts);
        finalResponse.setData(data);
        finalResponse.setResult(true);
        finalResponse.setMessage("Products from top 10 categories");

        return finalResponse;
    }

    private void processFetchedData(String categoryId, CJProductDataResponse newResponse) {
        long currentTime = System.currentTimeMillis();

        // Merge with existing data if any
        CJProductDataResponse mergedResponse = mergeResponses(
                categoryProductCache.get(categoryId), newResponse);

        // Update memory cache
        categoryProductCache.put(categoryId, mergedResponse);
        categoryFetchTime.put(categoryId, currentTime);

        // Save to file cache
        //saveToFileCache(categoryId, mergedResponse);
        saveToFileWithTimestamp(categoryId, mergedResponse,"category");

        // Send to processing channel
        // We cancelled for now the cached data sending to processing channel
        //sendToProcessingChannel(categoryId, mergedResponse);

        logger.info("Successfully processed data for category: {}", categoryId);
    }


    /**
     * Process the queue of categories that need to be fetched in the background
     * This method runs every 2 seconds to check for queued categories
     */
    private void processFetchQueue() {
        if (categoryFetchQueue.isEmpty()) {
            return;
        }

        // Use tryAcquire to avoid blocking if rate limit is exceeded
        if (!rateLimiter.tryAcquire()) {
            logger.debug("Rate limit exceeded, skipping queue processing this cycle");
            return;
        }

        String categoryId = categoryFetchQueue.poll();
        if (categoryId != null) {
            try {
                logger.debug("Processing queued fetch for category: {}", categoryId);
                CJProductDataResponse response = fetchCategoryWithRateLimit(categoryId);

                if(response!= null){
                    processFetchedData(categoryId, response);
                }
            } catch (Exception e) {
                logger.warn("Failed to process queued fetch for category: {}", categoryId, e);
                // Re-queue if failed for retry later
                categoryFetchQueue.offer(categoryId);
            }
        }
    }


    /**
     * Fetch products for a category with retry logic and exponential backoff
     */
    private CJProductDataResponse fetchCategoryProductsWithRetry(String categoryId) {
        int maxRetries = 3;
        int retryCount = 0;
        long baseDelayMs = 1100; // Start with 1.1 seconds

        while (retryCount <= maxRetries) {
            try {
                rateLimiter.acquire();
                CJProductDataResponse response = getProductsByCategoryId(categoryId, false);
                return response;

            } catch (Exception e) {
                retryCount++;
                if (retryCount > maxRetries) {
                    logger.error("Failed to fetch products for category {} after {} retries", categoryId, maxRetries, e);
                    return null;
                }

                // Exponential backoff: wait longer after each retry
                long delayMs = baseDelayMs * (long) Math.pow(2, retryCount);
                logger.warn("Retry {} for category {} after {}ms delay due to: {}",
                        retryCount, categoryId, delayMs, e.getMessage());

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warn("Interrupted during retry delay for category: {}", categoryId);
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Schedule a category for background refresh
     * Adds the category to the fetch queue if not already in progress or queued
     */

    /**
     * This method has som part of the code control for some checking
     * @param categoryId
     * @param allowStale
     * @return
     */
    public synchronized CJProductDataResponse getProductsByCategoryId(String categoryId, boolean allowStale) {

        CJProductDataResponse cachedResponse = categoryProductCache.get(categoryId);
        long currentTime = System.currentTimeMillis();
        long lastFetchTime = categoryFetchTime.getOrDefault(categoryId, 0L);


        // Check memory cache first
        if (cachedResponse != null && currentTime - lastFetchTime < PRODUCT_CACHE_DURATION) {
            logger.debug("Returning cached products for category: {}", categoryId);
            return cachedResponse;
        }

        // Check file cache if memory cache is missing or expired
        // Get the file cache data if available because cacheResponse is null
        CJProductDataResponse fileResponse = loadFromFileCache(categoryId);
        if (fileResponse != null) {
            mergeIntoMemoryCache(categoryId, fileResponse, currentTime - (PRODUCT_CACHE_DURATION / 2)); // Mark as somewhat stale
            logger.debug("Returning file-cached products for category: {}", categoryId);
            if (allowStale) {
                scheduleCategoryRefresh(categoryId); // Refresh in background
            }
            return fileResponse;
        }
        //Make api call and process the data
        try{
            // Check rate limit before making the request
            if (rateLimitService != null && !rateLimitService.canMakeRequest(categoryId)) {
                throw new RateLimitExceededException("Daily API limit reached for category: " + categoryId);
            }
            CJProductDataResponse apiResponse = fetchCategoryWithRateLimit(categoryId);
            if (apiResponse != null && apiResponse.isResult() && rateLimitService != null) {
                processFetchedData(categoryId, apiResponse);
                rateLimitService.recordRequest(categoryId);
                return apiResponse;
            }
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            // Check if this is a 429 error
            if (e.getMessage() != null && e.getMessage().contains("429")) {
                // Force mark this category as processed to avoid retries
                if (rateLimitService != null) {
                    rateLimitService.recordRequest(categoryId);
                }
                throw new RateLimitExceededException("API rate limit exceeded for category: " + categoryId, e);
            }
            throw new RuntimeException("Product fetch failed: " + e.getMessage(), e);
        }
        // Fallback: try to get any cached data
        return tryGetAnyCachedData(categoryId);
    }



    private CJProductDataResponse fetchCategoryWithRateLimit(String categoryId) {
        if (categoryFetchInProgress.putIfAbsent(categoryId, true) != null) {
            logger.debug("Fetch already in progress for category: {}", categoryId);
            return tryGetAnyCachedData(categoryId);
        }

        try {
            // Check if we can make request (under limit AND category not proceed today)
            if (!rateLimitService.canMakeRequest(categoryId)) {
                logger.debug("Skipping API call for category {} - limit reached or already processed", categoryId);
                return tryGetAnyCachedData(categoryId);
            }
            rateLimiter.acquire();// This is for per-second rate limiting
            logger.info("Fetching products for category: {} ({} requests today)", categoryId, rateLimitService.getCurrentRequestCount() +1);

            CJProductDataResponse response = productClient.getProductsByCategoryId(categoryId);

            // Only record successful requests
            if (response != null && response.isResult()) {

                rateLimitService.recordRequest(categoryId);

                // Update both memory and file cache
                categoryProductCache.put(categoryId, response);
                categoryFetchTime.put(categoryId, System.currentTimeMillis());
                //saveToFileCache(categoryId, response); // Save to file
                saveToFileWithTimestamp(categoryId, response, "category"); // Save to file

                logger.info("Successfully fetched and cached products for category: {}", categoryId);
            } else {
                logger.warn("API call failed for category: {}, not counting toward limit", categoryId);
            }
            return response;

        } catch (Exception e) {
            logger.error("Failed to fetch products for category: {}", categoryId, e);
            return tryGetAnyCachedData(categoryId); // Try to return any available cached data
        } finally {
            categoryFetchInProgress.remove(categoryId);
        }

    }

    // Add monitoring method
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void logRateLimitStatus() {
        int current = rateLimitService.getCurrentRequestCount();
        int remaining = rateLimitService.getRemainingRequests();
        String timeUntilReset = rateLimitService.getTimeUntilReset();

        logger.info("API Usage: {}/1000 requests used, {} remaining. Reset in: {}",
                current, remaining, timeUntilReset);

        if (remaining < 100) {
            logger.warn("LOW API QUOTA: Only {} requests remaining until reset", remaining);
        }
    }

    private CJProductDataResponse tryGetAnyCachedData(String categoryId) {
        // Try memory cache first
        CJProductDataResponse memoryCache = categoryProductCache.get(categoryId);
        if (memoryCache != null) {
            logger.warn("Using memory-cached data for category: {} due to API failure", categoryId);
            return memoryCache;
        }

        // Try file cache as fallback
        CJProductDataResponse fileCache = loadFromFileCache(categoryId);
        if (fileCache != null) {
            logger.warn("Using file-cached data for category: {} due to API failure", categoryId);
            // Update memory cache from file
            categoryProductCache.put(categoryId, fileCache);
            categoryFetchTime.put(categoryId, System.currentTimeMillis());
            return fileCache;
        }

        throw new RuntimeException("No cached data available for category: " + categoryId);
    }



    public synchronized List<CJCategoryDataResponse.CategoryThird> getTop10Categories(){
        CJCategoryDataResponse categoryDataResponse = fetchCategoryList();
        List<CJCategoryDataResponse.CategoryThird> topCategories = new ArrayList<>();

        if (categoryDataResponse != null && categoryDataResponse.isResult() && categoryDataResponse.getData() != null) {
            // Extract all third-level categories
            List<CJCategoryDataResponse.CategoryThird> allThirdCategories = new ArrayList<>();

            for (CJCategoryDataResponse.CategoryData categoryData : categoryDataResponse.getData()) {
                if (categoryData != null && categoryData.getCategoryFirstList() != null) {
                    for (CJCategoryDataResponse.CategorySecond categorySecond : categoryData.getCategoryFirstList()) {
                        if (categorySecond != null && categorySecond.getCategorySecondList() != null) {
                            allThirdCategories.addAll(categorySecond.getCategorySecondList());
                        }
                    }
                }
            }

            // Sort categories by name and take top 10
            topCategories = allThirdCategories.stream()
                    .filter(Objects::nonNull)
                    .filter(category -> category.getCategoryName() != null) // Ensure name is not null
                    .sorted(Comparator.comparing(CJCategoryDataResponse.CategoryThird::getCategoryName))
                    .limit(10)
                    .collect(Collectors.toList());

            logger.info("Found {} total categories, returning top {} by name",
                    allThirdCategories.size(), topCategories.size());
        }
        return topCategories;
    }

    // Optional: Method to clear cache
    public synchronized void clearProductCache() {
        categoryProductCache.clear();
        categoryFetchTime.clear();
        logger.info("Product cache cleared");
    }

    // Optional: Method to clear cache for specific category
    public synchronized void clearProductCache(String categoryId) {
        categoryProductCache.remove(categoryId);
        categoryFetchTime.remove(categoryId);
        logger.info("Cache cleared for category: {}", categoryId);
    }

    /**
     * Initialisation method for creating cache directory if it doesn't exist'
     */
    private void initializeCacheDirectory(){
        try{

            Path cacheDir = Paths.get(cacheDirectory);
            if(!Files.exists(cacheDir)){
                Files.createDirectories(cacheDir);
                logger.info("Created cache directory at {} ", cacheDirectory);
            }
        }catch (IOException ioe){
            logger.error("Failed to initialize cache directory: {}", cacheDirectory, ioe);
        }
    }

    /**
     * Useful method that allows me to load cached data from file
     */
    private void loadCacheFromFiles(){
        try{

            Path cacheDir = Paths.get(cacheDirectory);
            if(!Files.exists(cacheDir)) return;

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            Files.list(cacheDir)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(filePath -> {
                        try{
                          String fileName = filePath.getFileName().toString();
                          String categoryId = fileName.replace("category-", "").replace(".json", "");

                          CJProductDataResponse response = mapper.readValue(filePath.toFile(), CJProductDataResponse.class);
                          long lastModified = Files.getLastModifiedTime(filePath).toMillis();


                          categoryProductCache.put(categoryId, response);
                          categoryFetchTime.put(categoryId, lastModified);

                          logger.info("Loaded cached data for category: {}", categoryId);
                        }catch(Exception e){
                            logger.warn("Failed to load cache file: {}", filePath, e);
                        }
                    });
            logger.info("Loaded {} cached categories from files", categoryProductCache.size());
        } catch (IOException e) {
            logger.error("Failed to load cache from files", e);
        }
    }

    /**
     * Useful method to save cached data to file
     * @param categoryId
     * @param response
     */
    private synchronized void saveToFileCache(String categoryId, CJProductDataResponse response){
        try{
             ObjectMapper mapper = new ObjectMapper();
             mapper.registerModule(new JavaTimeModule());
             mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
             mapper.disable(SerializationFeature.INDENT_OUTPUT);

             String filename = "category-" + categoryId + ".json";
             Path filePath = Paths.get(cacheDirectory, filename);
             mapper.writeValue(filePath.toFile(), response);

            logger.debug("Saved cache for category {} to file: {}", categoryId, filename);
        }catch (IOException ioe){
            logger.error("Failed to save cache to file for category: {}", categoryId, ioe);
        }
    }

    /**
     * Saves fetched data to file with timestamp-based naming
     * @param categoryId the category ID (or "all" for general products)
     * @param response the API response data
     * @param fetchType type of fetch ("hourly", "daily", "category")
     */
    private synchronized void saveToFileWithTimestamp(String categoryId,
                                                      CJProductDataResponse response,
                                                      String fetchType) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            mapper.enable(SerializationFeature.INDENT_OUTPUT); // Keep formatting for readability

            // Create timestamp-based filename
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String filename = String.format("%s-%s-%s.json", fetchType, categoryId, timestamp);

            Path filePath = Paths.get(cacheDirectory, filename);

            // Create directory if it doesn't exist
            Files.createDirectories(filePath.getParent());

            mapper.writeValue(filePath.toFile(), response);

            logger.info("Saved {} data for category {} to file: {}", fetchType, categoryId, filename);

            // Optional: Clean up old files (keep last 24 hours)
            //cleanOldFiles(fetchType, categoryId);

        } catch (IOException ioe) {
            logger.error("Failed to save {} data to file for category: {}", fetchType, categoryId, ioe);
        }
    }

    // Optional cleanup method
    private void cleanOldFiles(String fetchType, String categoryId) {
        try {
            File dir = new File(cacheDirectory);
            File[] oldFiles = dir.listFiles((d, name) ->
                    name.startsWith(fetchType + "-" + categoryId) &&
                            name.endsWith(".json"));

            if (oldFiles != null) {
                Arrays.sort(oldFiles, Comparator.comparing(File::lastModified).reversed());

                // Keep only the latest 24 files (assuming hourly fetches)
                for (int i = 24; i < oldFiles.length; i++) {
                    Files.deleteIfExists(oldFiles[i].toPath());
                    logger.debug("Deleted old file: {}", oldFiles[i].getName());
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to clean up old files", e);
        }
    }

    /**
     * Useful method to load cached data from a specific file with categoryId
     * @param categoryId
     * @return
     */

    private CJProductDataResponse loadFromFileCache(String categoryId) {
        try {
            String filename = "category-" + categoryId + ".json";
            Path filePath = Paths.get(cacheDirectory, filename);

            if (!Files.exists(filePath)) {
                return null;
            }

            // Check if file cache is still valid
            long lastModified = Files.getLastModifiedTime(filePath).toMillis();
            if (System.currentTimeMillis() - lastModified > PRODUCT_CACHE_DURATION) {
                logger.debug("File cache expired for category: {}", categoryId);
                return null;
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            CJProductDataResponse response = mapper.readValue(filePath.toFile(), CJProductDataResponse.class);

            logger.debug("Loaded category {} from file cache", categoryId);
            return response;

        } catch (Exception e) {
            logger.warn("Failed to load from file cache for category: {}", categoryId, e);
            return null;
        }
    }


    /**
     *
     * @param categoryId
     */
    private void scheduleCategoryRefresh(String categoryId) {
        // Check if already in progress or already queued
        if (categoryFetchInProgress.containsKey(categoryId)) {
            logger.debug("Refresh already in progress for category: {}", categoryId);
            return;
        }

        if (categoryFetchQueue.contains(categoryId)) {
            logger.debug("Refresh already queued for category: {}", categoryId);
            return;
        }

        // Add to queue for background processing
        categoryFetchQueue.offer(categoryId);
        logger.debug("Scheduled background refresh for category: {}", categoryId);
    }

    /**
     *
     * @param existing
     * @param newResponse
     * @return
     */
    private CJProductDataResponse mergeResponses (CJProductDataResponse existing, CJProductDataResponse newResponse){
        if (existing == null) {
            return newResponse;
        }

        if (newResponse == null || !newResponse.isResult() || newResponse.getData() == null) {
            return existing;
        }

        // Create a merged response
        CJProductDataResponse merged = new CJProductDataResponse();
        merged.setResult(true);
        merged.setMessage("Merged response from cache memory and Api call");

        CJProductData mergedData = new CJProductData();
        List<CJProduct> mergedProducts = new ArrayList<>();

        // Add existing products if available
        if (existing.getData() != null && existing.getData().getList() != null) {
            mergedProducts.addAll(existing.getData().getList());
        }

        // Add new products, avoiding duplicates
        if (newResponse.getData() != null && newResponse.getData().getList() != null) {
            List<String> existingProductIds = mergedProducts.stream()
                    .map(CJProduct::getPid)
                    //.collect(Collectors.toList());
                    .toList();

            newResponse.getData().getList().stream()
                    .filter(product -> !existingProductIds.contains(product.getPid()))
                    .forEach(mergedProducts::add);
        }

        mergedData.setList(mergedProducts);
        merged.setData(mergedData);

        return merged;
    }

    private void mergeIntoMemoryCache(String categoryId, CJProductDataResponse fileResponse,  long timestamp) {
        CJProductDataResponse existing = categoryProductCache.get(categoryId);

        if (existing == null) {
            // No existing data, just put the file data
            categoryProductCache.put(categoryId, fileResponse);
            categoryFetchTime.put(categoryId, timestamp);
        } else {
            // Merge existing memory data with file data
            CJProductDataResponse merged = mergeResponses(existing, fileResponse);
            categoryProductCache.put(categoryId, merged);
            categoryFetchTime.put(categoryId, Math.max(
                    categoryFetchTime.getOrDefault(categoryId, 0L),
                    timestamp
            ));
        }
    }

    /**
     *
     * @param categoryId
     * @param response
     */
    /*private void sendToProcessingChannel(String categoryId, CJProductDataResponse response) {

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            String jsonData = mapper.writeValueAsString(response);

            Message<String> message = MessageBuilder.withPayload(jsonData)
                    .setHeader("categoryId", categoryId)
                    .setHeader("fetchTime", System.currentTimeMillis())
                    .setHeader("source", "CJProductService")
                    .build();

            boolean sent = productDataChannel.send(message);

            if (sent) {
                logger.debug("Successfully sent data for category {} to processing channel", categoryId);
            } else {
                logger.warn("Failed to send data for category {} to processing channel", categoryId);
                // You might want to implement retry logic here
            }

        } catch (Exception e) {
            logger.error("Failed to send data to processing channel for category: {}", categoryId, e);
        }
    }*/
}
