package org.linlinjava.litemall.goods.infrastructure.acl.ocs;

import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ACL adapter to the OCS searcher REST service (port 8534 by default).
 *
 * <p>API contract verified at runtime against {@code commerceexperts/
 * ocs-search-service}:
 * {@code GET {search-url}/search-api/v1/search/{index}?q={q}&offset={o}&limit={n}}
 * returns
 * {@code {"slices":[{"matchCount":N,"hits":[{"document":{"id":"…","data":{…}}}]}]}}.
 * This adapter maps that envelope onto the {@code goodsList} DTO the SPA already
 * consumes ({@code id,name,brief,picUrl,retailPrice,counterPrice,brand,
 * categoryNames}) plus {@code total/offset/limit} — no SQL fallback on the
 * search path. (The previous {@code /search/{index}} path returning the raw map
 * was wrong and unmapped.)
 */
@Component
public class OcsSearchClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OcsSearchClient.class);

    // See OcsIndexerClient — owned per-client to avoid bean conflict with the
    // shared core RestTemplate.
    private final RestTemplate restTemplate = new RestTemplate();
    private final LitemallSearchProperties properties;

    public OcsSearchClient(LitemallSearchProperties properties) {
        this.properties = properties;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> search(String query, int offset, int limit) {
        String url = UriComponentsBuilder
                .fromHttpUrl(properties.getSearchUrl())
                .pathSegment("search-api", "v1", "search", properties.getIndexName())
                .queryParam("q", query == null ? "" : query)
                .queryParam("offset", offset)
                .queryParam("limit", limit)
                .toUriString();
        try {
            Map<String, Object> raw = restTemplate.getForObject(url, Map.class);
            return toGoodsListResponse(raw, offset, limit);
        } catch (RestClientException e) {
            LOGGER.warn("OCS search failed for q={} ({}): {}", query, url, e.getMessage());
            return emptyResponse(offset, limit);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toGoodsListResponse(Map<String, Object> raw, int offset, int limit) {
        List<Map<String, Object>> goodsList = new ArrayList<>();
        long total = 0L;
        if (raw != null) {
            List<Map<String, Object>> slices = (List<Map<String, Object>>) raw.get("slices");
            if (slices != null) {
                for (Map<String, Object> slice : slices) {
                    Number matchCount = (Number) slice.get("matchCount");
                    if (matchCount != null) {
                        total += matchCount.longValue();
                    }
                    List<Map<String, Object>> hits = (List<Map<String, Object>>) slice.get("hits");
                    if (hits == null) {
                        continue;
                    }
                    for (Map<String, Object> hit : hits) {
                        Map<String, Object> item = toGoodsListItem((Map<String, Object>) hit.get("document"));
                        if (item != null) {
                            goodsList.add(item);
                        }
                    }
                }
            }
        }
        Map<String, Object> response = new HashMap<>();
        response.put("total", total);
        response.put("offset", offset);
        response.put("limit", limit);
        response.put("goodsList", goodsList);
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toGoodsListItem(Map<String, Object> document) {
        if (document == null) {
            return null;
        }
        Map<String, Object> item = new HashMap<>();
        item.put("id", document.get("id"));
        Map<String, Object> data = (Map<String, Object>) document.get("data");
        if (data != null) {
            Object discount = data.get("discount_price");
            item.put("name", data.get("title"));
            item.put("brief", data.get("description"));
            item.put("picUrl", data.get("image_url"));
            item.put("retailPrice", discount != null ? discount : data.get("price"));
            item.put("counterPrice", data.get("price"));
            item.put("brand", data.get("brand"));
            item.put("categoryNames", data.get("category_names"));
        }
        return item;
    }

    private Map<String, Object> emptyResponse(int offset, int limit) {
        Map<String, Object> response = new HashMap<>();
        response.put("total", 0L);
        response.put("offset", offset);
        response.put("limit", limit);
        response.put("goodsList", new ArrayList<>());
        return response;
    }
}
