package org.linlinjava.litemall.goods.application.discovery;

import org.linlinjava.litemall.goods.application.search.SearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovery rails over the unified OCS index (local + CJ), each shaping the SAME index a
 * different way (Relevant Search ch. 7 — sort/boost the one index for different needs):
 *
 * <ul>
 *   <li><b>SuperDeals</b> — popularity-first: empty-query browse sorted by {@code -listed_num}
 *       (CJ platform listing count = proven demand). Value is carried by the global scoring;
 *       a price-value tie-break is a documented tuning follow-up.</li>
 *   <li><b>New Arrivals</b> — recency-first: empty-query browse sorted by {@code -created_epoch}
 *       (product creation date), capped to an affordable price band.</li>
 * </ul>
 *
 * Both return the {@code goodsList} item shape ({@code id/name/brief/picUrl/retailPrice/
 * counterPrice/brand/categoryNames/source}) the SPA rails already render, so
 * {@code /srv/goods/index} can source {@code hotGoodsList}/{@code newGoodsList} from here with no
 * SPA change. The "All Products" / navbar browse keeps using {@code /srv/search} (default
 * scoring = the global price·popularity·reviews·in-stock boost).
 */
@Service
public class DiscoveryService {

    private final SearchService searchService;

    /** Upper price bound (retail) for the New Arrivals "affordable" band; <=0 disables the cap. */
    private final int affordableMaxPrice;

    public DiscoveryService(SearchService searchService,
                            @Value("${litemall.discovery.affordable-max-price:100}") int affordableMaxPrice) {
        this.searchService = searchService;
        this.affordableMaxPrice = affordableMaxPrice;
    }

    /** SuperDeals rail: most-listed (popular) products across local + CJ. */
    public List<Map<String, Object>> superDeals(int limit) {
        Map<String, Object> result = searchService.search("", 1, limit, "-listed_num", new HashMap<>());
        return goodsList(result);
    }

    /** New Arrivals rail: newest products within the affordable band, across local + CJ. */
    public List<Map<String, Object>> newArrivals(int limit) {
        Map<String, String> filters = new HashMap<>();
        if (affordableMaxPrice > 0) {
            // OCS interval-filter syntax on the numeric price facet: price=<min>,<max>.
            filters.put("price", "0," + affordableMaxPrice);
        }
        Map<String, Object> result = searchService.search("", 1, limit, "-created_epoch", filters);
        return goodsList(result);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> goodsList(Map<String, Object> result) {
        Object list = result != null ? result.get("goodsList") : null;
        return list instanceof List ? (List<Map<String, Object>>) list : List.of();
    }
}
