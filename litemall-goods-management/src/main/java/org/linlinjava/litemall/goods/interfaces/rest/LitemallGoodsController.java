package org.linlinjava.litemall.goods.interfaces.rest;


import com.github.pagehelper.PageInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.core.util.ApiResponse;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.db.service.LitemallAdService;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallCouponService;
import org.linlinjava.litemall.goods.application.comment.CommentStatsService;
import org.linlinjava.litemall.goods.application.goods.LitemallGoodsManagementService;
import org.linlinjava.litemall.goods.application.goods.cj.CjGoodsDetailService;
import org.linlinjava.litemall.goods.application.goods.cj.CjGoodsVideoService;
import org.linlinjava.litemall.goods.application.discovery.DiscoveryService;
import org.linlinjava.litemall.goods.application.search.SearchService;
import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.domain.model.dto.goods.ReduceStockRequest;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.manufacturer.LitemallManufacturerId;
import org.linlinjava.litemall.goods.infrastructure.configuration.RabbitMqConfig;
import org.linlinjava.litemall.goods.infrastructure.messaging.source.MessageProducer;
import org.linlinjava.litemall.goods.infrastructure.services.api.LitemallCatalogService;
import org.linlinjava.litemall.goods.infrastructure.services.api.LitemallGoodsServiceApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("srv/goods")
public class LitemallGoodsController {


    @Autowired
    private LitemallGoodsServiceApi goodsServiceApi;
    @Autowired
    private LitemallCatalogService categoryServiceApi;
    @Autowired
    private LitemallGoodsManagementService goodsManagementService;
    @Autowired
    private CjGoodsDetailService cjGoodsDetailService;
    @Autowired
    private CjGoodsVideoService cjGoodsVideoService;
    // Demand-Driven CJ Enrichment: viewing a shallow CJ goods triggers an async enrich-in-place.
    @Autowired
    private org.linlinjava.litemall.goods.application.search.CjOnDemandEnrichmentService cjOnDemandEnrichmentService;
    @Autowired
    private SearchService searchService;
    // OCS-backed discovery rails (SuperDeals / New Arrivals), unified local + CJ, price·popularity·
    // recency·reviews boosted — replaces the DB-only is_new/is_hot lists on the home payload.
    @Autowired
    private DiscoveryService discoveryService;
    // Batched per-goods review stats (star avg + count) decorating the listing surfaces.
    @Autowired
    private CommentStatsService commentStatsService;
    // Flash deals (price-swap lifecycle): serves the live-deal block for the detail-page countdown.
    @Autowired
    private org.linlinjava.litemall.goods.application.deals.FlashDealService flashDealService;

    // Home-page marketing data sourced from litemall-db (mirrors the monolith's
    // WxHomeController): banners (ads), channels (channel categories), coupons.
    @Autowired
    private LitemallAdService adService;
    @Autowired
    private LitemallCategoryService categoryService;
    @Autowired
    private LitemallCouponService couponService;

    @Autowired
    private MessageProducer messageProducer;

    private static final ArrayBlockingQueue<Runnable> WORK_QUEUE = new ArrayBlockingQueue<>(9);
    private static final RejectedExecutionHandler HANDLER = new ThreadPoolExecutor.CallerRunsPolicy();
    private static final ThreadPoolExecutor executorService = new ThreadPoolExecutor(9, 9, 1000, TimeUnit.MILLISECONDS, WORK_QUEUE, HANDLER);


    /**
     *
     * @return
     */
    @GetMapping("/index")
    public Object index(){

        Callable<List> bannerListCallable = () -> adService.queryIndex();

        Callable<List> channelListCallable = () -> categoryService.queryChannel();

        // Public home payload: top coupons available to claim (no logged-in user here).
        Callable<List> couponListCallable = () -> couponService.queryList(0, 3);

        // New Arrivals (newest + affordable) and SuperDeals (most-listed/popular) now come from the
        // unified OCS index — spanning local AND CJ, price·popularity·recency·reviews boosted — instead
        // of the DB-only is_new/is_hot flags (which CJ rows never carry). Degrade to the DB lists if
        // OCS is unavailable so the home page still renders.
        Callable<List> newGoodsListCallable = () -> {
            try {
                return discoveryService.newArrivals(SystemConfig.getNewLimit());
            } catch (RuntimeException ex) {
                return goodsManagementService.goodsByNew(0, SystemConfig.getNewLimit());
            }
        };

        Callable<List> hotGoodsListCallable = () -> {
            try {
                return discoveryService.superDeals(SystemConfig.getHotLimit());
            } catch (RuntimeException ex) {
                return goodsManagementService.goodsByHot(0, SystemConfig.getHotLimit());
            }
        };

        //Callable<List> brandListCallable = () -> brandService.query(0, SystemConfig.getBrandLimit());

        //Callable<List> topicListCallable = () -> topicService.queryList(0, SystemConfig.getTopicLimit());

        //团购专区
        //Callable<List> grouponListCallable = () -> grouponService.queryList(0, 5);

        //Callable<List> floorGoodsListCallable = this::getCategoryList;
        FutureTask<List> bannerTask = new FutureTask<>(bannerListCallable);
        FutureTask<List> channelTask = new FutureTask<>(channelListCallable);
        FutureTask<List> couponListTask = new FutureTask<>(couponListCallable);
        FutureTask<List> newGoodsListTask = new FutureTask<>(newGoodsListCallable);
        FutureTask<List> hotGoodsListTask = new FutureTask<>(hotGoodsListCallable);
        //FutureTask<List> brandListTask = new FutureTask<>(brandListCallable);
        //FutureTask<List> topicListTask = new FutureTask<>(topicListCallable);
        //FutureTask<List> grouponListTask = new FutureTask<>(grouponListCallable);
        //FutureTask<List> floorGoodsListTask = new FutureTask<>(floorGoodsListCallable);

        executorService.submit(bannerTask);
        executorService.submit(channelTask);
        executorService.submit(couponListTask);
        executorService.submit(newGoodsListTask);
        executorService.submit(hotGoodsListTask);
        /*executorService.submit(brandListTask);
        executorService.submit(topicListTask);
        executorService.submit(grouponListTask);*/
        //executorService.submit(floorGoodsListTask);

        Map<String, Object> entity = new HashMap<>();
        try {
            entity.put("banner", bannerTask.get());
            entity.put("channel", channelTask.get());
            entity.put("couponList", couponListTask.get());
            entity.put("newGoodsList", newGoodsListTask.get());
            entity.put("hotGoodsList", hotGoodsListTask.get());
            /*entity.put("brandList", brandListTask.get());
            entity.put("topicList", topicListTask.get());
            entity.put("grouponList", grouponListTask.get());*/
            //entity.put("floorGoodsList", floorGoodsListTask.get());
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseUtil.ok(entity);

    }


    /**
     *
     * @param categoryId required
     * @param brandId required
     * @param keyword
     * @param isNew
     * @param isHot
     * @param page
     * @param limit
     * @param sort
     * @param order
     * @return
     */
    @GetMapping("/list")
    public Object listGoods(
            Integer categoryId,
            Integer brandId,
            String keyword,
            Boolean isNew,
            Boolean isHot,
            // @LoginUser Integer userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer limit,
            @Sort(accepts = {"add_time", "retail_price", "name"}) @RequestParam(defaultValue = "add_time") String sort,
            @Order @RequestParam(defaultValue = "desc") String order
    ) {
        // Customer browse routes through the unified OCS index so the listing spans BOTH local and CJ
        // products and carries the same facets/aggregations as /srv/search. brandId / isNew / isHot are
        // local-only signals with no indexed field, so a request constrained by them (or an OCS outage)
        // falls back to the local-DB path below.
        boolean ocsServable = brandId == null && !Boolean.TRUE.equals(isNew) && !Boolean.TRUE.equals(isHot);
        if (ocsServable) {
            try {
                Map<String, String> filters = new HashMap<>();
                if (categoryId != null) {
                    filters.put("category_ids", String.valueOf(categoryId));
                }
                Map<String, Object> result = searchService.search(keyword, page, limit, ocsSort(sort, order), filters);

                Map<String, Object> entity = new HashMap<>();
                entity.put("list", result.get("goodsList"));   // legacy key; unified local+CJ hit list
                entity.put("total", result.get("total"));
                entity.put("page", result.get("page"));
                entity.put("limit", result.get("limit"));
                entity.put("pages", result.get("totalPages"));
                entity.put("filters", result.get("filters"));  // OCS facets/aggregations (new)
                entity.put("filterCategoryList", null);
                entity.put("source", "ocs");
                return ResponseUtil.ok(entity);
            } catch (RuntimeException ex) {
                // OCS unavailable — degrade to the local-DB listing rather than failing the page.
            }
        }

        LitemallCategoryId catId = categoryId != null ? new LitemallCategoryId(categoryId) : null;
        LitemallManufacturerId manufacturerId = brandId != null ? new LitemallManufacturerId(brandId) : null;
        List<LitemallGoodsAggregate> goodsList = goodsServiceApi.getGoodsBySelective(catId, manufacturerId, keyword, isHot, isNew, page, limit, sort);

        PageInfo<LitemallGoodsAggregate> pagedList = PageInfo.of(goodsList);

        Map<String, Object> entity = new HashMap<>();
        entity.put("list", commentStatsService.withStats(goodsList));
        entity.put("total", pagedList.getTotal());
        entity.put("page", pagedList.getPageNum());
        entity.put("limit", pagedList.getPageSize());
        entity.put("pages", pagedList.getPages());
        entity.put("filterCategoryList", null);
        entity.put("source", "db");

        return ResponseUtil.ok(entity);
    }

    /**
     * Map the local browse {@code sort}/{@code order} onto the OCS sort syntax ({@code field} asc,
     * {@code -field} desc). Only fields that exist in {@code litemall_index} are mappable; {@code add_time}
     * has no indexed counterpart, so it yields {@code null} (OCS default relevance ordering).
     */
    private String ocsSort(String sort, String order) {
        if (sort == null) {
            return null;
        }
        String field = switch (sort) {
            case "retail_price" -> "price";
            case "name" -> "title";
            default -> null; // add_time and anything else → default OCS ordering
        };
        if (field == null) {
            return null;
        }
        return "desc".equalsIgnoreCase(order) ? "-" + field : field;
    }

    @GetMapping("by-category")
    public Object goodsByCategory(@RequestParam Integer id) {

        LitemallCategoryId categoryId = new LitemallCategoryId(id);
        // Current category
        LitemallCategoryAggregate currentCategory = categoryServiceApi.getCategoryById(categoryId);
        if(currentCategory == null){
            return ResponseUtil.badArgumentValue();
        }
        List<LitemallGoodsAggregate> goodsByCategory = goodsServiceApi.getGoodsByCategoryId(currentCategory.getCategoryId(), 0, 80);

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("currentCategory", currentCategory);
        data.put("goodsCategory", commentStatsService.withStats(goodsByCategory));
        return ResponseUtil.ok(data);
    }

    /**
     * Product details page "Everyone is watching" recommended products
     *
     * @param id, 商品ID
     * @return Recommended products on product details page
     */
    @GetMapping("related")
    public Object related(@NotBlank String id) {
        // CJ Dropshipping products carry a cj_<uuid> id and are OCS-only (no
        // litemall_goods row, no local category), so the category-based
        // recommendation can't run for them. Return an empty related list
        // instead of 400ing on the non-numeric id (mirrors /detail's cj branch).
        if (CjGoodsDetailService.isCjId(id)) {
            return ResponseUtil.okList(java.util.Collections.emptyList());
        }
        LitemallGoodsId goodsId = new LitemallGoodsId(Integer.valueOf(id.trim()));
        LitemallGoodsAggregate goods = goodsServiceApi.getGoodsById(goodsId);
        if (goods == null) {
            return ResponseUtil.badArgumentValue();
        }

        // The current product recommendation algorithm only recommends other products of the same category.
        LitemallCategoryId cid = new LitemallCategoryId(goods.getCategoryId().getId());

        // Find six related products
        int related = 6;
        List<LitemallGoodsAggregate> goodsList = goodsServiceApi.getGoodsByCategoryId(cid, 0, related);
        return ResponseUtil.okList(commentStatsService.withStats(goodsList));
    }


    /**
     * Live flash-deal block for a goods: {@code {dealPrice, originalPrice, endEpoch, stock,
     * claimed, claimedPct}} or {@code data:null} when no deal is live. The detail page polls
     * this once to render the countdown/claimed bar; the deal PRICE itself already rides the
     * normal detail payload (price-swap semantics — retail_price IS the deal price while live).
     * CJ ids have no deals by design (652 on authoring), so they short-circuit to null.
     */
    @GetMapping("/deal")
    public Object liveDeal(@NotBlank String id) {
        if (CjGoodsDetailService.isCjId(id)) {
            return ResponseUtil.ok(null);
        }
        return ResponseUtil.ok(flashDealService.liveDealBlock(Integer.valueOf(id.trim())));
    }

    @GetMapping("/detail")
    public Object privateGoodsDetails(@NotBlank String id) {
        // CJ Dropshipping products carry a cj_<uuid> id (not numeric) and live in OCS only — they
        // have no litemall_goods row, so the DB aggregation can't serve them. Route those to the
        // live CJ detail fetch; everything else is a local numeric goods id.
        if (CjGoodsDetailService.isCjId(id)) {
            // Viewing an unpromoted CJ product is the strongest demand signal there is —
            // enrich (and thereby promote) it in the background while the page is read.
            cjOnDemandEnrichmentService.requestForPid(CjGoodsDetailService.pidOf(id));
            return cjGoodsDetailService.detail(id);
        }
        LitemallGoodsId goodsId = new LitemallGoodsId(Integer.valueOf(id.trim()));
        // Shallow CJ goods (vid-less placeholder SKU) become orderable in the background.
        cjOnDemandEnrichmentService.requestForGoods(goodsId.getId());
        return goodsManagementService.goodsDetail(goodsId, executorService, HANDLER, WORK_QUEUE);
    }


    /**
     * CJ product videos for a goods detail page (Wave 3). Separate from /detail so the
     * {goods, products, ...} contract stays untouched and the SPA can lazy-load videos.
     * Accepts both id forms like /detail; non-CJ goods, no videos, or CJ down → empty list.
     */
    @GetMapping("/videos")
    public Object goodsVideos(@NotBlank String id) {
        return ResponseUtil.okList(cjGoodsVideoService.videosFor(id));
    }

    @GetMapping("/messages")
    //public Object sendMessage(@NotNull String message) {
    public Object sendMessage(@RequestParam @NotBlank String message) {
        messageProducer.sendMessage(
                RabbitMqConfig.EXCHANGE_NAME,
                RabbitMqConfig.QUEUE_NAME,
                message
        );

        Map<String, Object> data = new HashMap<>();
        data.put("message", message);
        return data;
    }


    @PostMapping("/stock/reduce" )
    public ResponseEntity<ApiResponse<Void>> reduceStock(@RequestBody ReduceStockRequest request) {
        // Validate input
        if (request.getProductId() == null || request.getNumber() == null) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.fail(400, "ID and quantity are required"));
        }
        // Process the request
        LitemallGoodsProductId goodsProductId = new LitemallGoodsProductId(request.getProductId().toString());
        boolean reduced = goodsManagementService.reduceStock(goodsProductId, request.getNumber().shortValue());
        if (!reduced) {
            // HTTP 200 + non-zero errno so Feign callers read the failure from the
            // body instead of an exception path (order aborts placement on errno != 0).
            return ResponseEntity.ok(ApiResponse.fail(631,
                    "insufficient stock for product " + request.getProductId()));
        }
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * Inverse of {@code /stock/reduce}: returns previously-reserved stock. Called by
     * order's rollback/cancellation compensation, so it must never oversubtract —
     * it only adds, guarded to existing non-deleted products.
     */
    @PostMapping("/stock/restore")
    public ResponseEntity<ApiResponse<Void>> restoreStock(@RequestBody ReduceStockRequest request) {
        if (request.getProductId() == null || request.getNumber() == null) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.fail(400, "ID and quantity are required"));
        }
        LitemallGoodsProductId goodsProductId = new LitemallGoodsProductId(request.getProductId().toString());
        boolean restored = goodsManagementService.restoreStock(goodsProductId, request.getNumber().shortValue());
        if (!restored) {
            return ResponseEntity.ok(ApiResponse.fail(632,
                    "unknown or deleted product " + request.getProductId()));
        }
        return ResponseEntity.ok(ApiResponse.success());
    }


    /** Total on-sale goods count (litemall-wx-api {@code /wx/goods/count} parity); anonymous. */
    @GetMapping("/count")
    public Object count() {
        return ResponseUtil.ok(goodsServiceApi.getGoodsOnSale());
    }

    @GetMapping("/goodsdetail")
    public Object getGoodsDetail(@NotNull Integer id) {
        LitemallGoodsId goodsId = new LitemallGoodsId(id);
        // Also fires on the order service's facade fetch — including its submit-block retry
        // path, which deliberately re-reads the goods to trigger this enrichment hook.
        cjOnDemandEnrichmentService.requestForGoods(id);
        return goodsManagementService.getGoodsAggregateById(goodsId);
    }

    @GetMapping("/product")
    public Object getGoodsProductByGoodsId(@NotNull Integer id) {
        LitemallGoodsId goodsId = new LitemallGoodsId(id);
        return goodsManagementService.getGoodsProductAggregateByGoodsId(goodsId);
    }

    @GetMapping("/attribute")
    public Object getGoodsAttributeByGoodsId(@NotNull Integer id) {
        LitemallGoodsId goodsId = new LitemallGoodsId(id);
        return goodsManagementService.getGoodsAttributeAggregateByGoodsId(goodsId);
    }

    @GetMapping("/specification")
    public Object getGoodsSpecificationsByGoodsId(@NotNull Integer id) {
        LitemallGoodsId goodsId = new LitemallGoodsId(id);
        return goodsManagementService.getGoodsSpecificationAggregateByGoodsId(goodsId);
    }


    /**
     * Bulk goods lookup keyed by plain integer goods id. Keying by the
     * {@code LitemallGoodsId} value object serialized the map keys as JVM identity
     * strings ({@code LitemallGoodsId@3b488a9a}), which consumers cannot index.
     */
    @PostMapping("/batch")
    public Map<Integer, LitemallGoodsAggregate> batchGoods(@RequestBody Set<Integer> goodsIds) {
        List<LitemallGoodsAggregate> goodsList = goodsServiceApi.getAllGoodByIds(goodsIds.stream().map(LitemallGoodsId::new).toList());
        return goodsList.stream().collect(Collectors.toMap(g -> g.getGoodsId().getId(), Function.identity()));
    }
}

