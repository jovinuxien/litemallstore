package org.linlinjava.litemall.goods.utils;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HomeCacheManager {

    public static final boolean ENABLE = false;
    public static final String INDEX = "index";
    public static final String CATALOG = "catalog";
    public static final String GOODS = "goods";

    private static ConcurrentHashMap<String, Map<String, Object>> cacheDataList = new ConcurrentHashMap<>();

    /**
     * Cache home page data
     *
     * @param data
     */
    public static void loadData(String cacheKey, Map<String, Object> data) {
        Map<String, Object> cacheData = cacheDataList.get(cacheKey);
        //There is a record，then discard first
        if (cacheData != null) {
            cacheData.remove(cacheKey);
        }

        cacheData = new HashMap<>();
        //deep copy
        cacheData.putAll(data);
        cacheData.put("isCache", "true");
        //Set the cache validity period to 10 minutes
        cacheData.put("expireTime", LocalDateTime.now().plusMinutes(10));
        cacheDataList.put(cacheKey, cacheData);
    }

    public static Map<String, Object> getCacheData(String cacheKey) {
        return cacheDataList.get(cacheKey);
    }

    /**
     * Determine whether there is data in the cache
     *
     * @return
     */
    public static boolean hasData(String cacheKey) {
        if (!ENABLE)
            return false;

        Map<String, Object> cacheData = cacheDataList.get(cacheKey);
        if (cacheData == null) {
            return false;
        } else {
            LocalDateTime expire = (LocalDateTime) cacheData.get("expireTime");
            if (expire.isBefore(LocalDateTime.now())) {
                return false;
            } else {
                return true;
            }
        }
    }

    /**
     * clear all cache
     */
    public static void clearAll() {
        cacheDataList = new ConcurrentHashMap<>();
    }

    /**
     * Clear cache data
     */
    public static void clear(String cacheKey) {
        Map<String, Object> cacheData = cacheDataList.get(cacheKey);
        if (cacheData != null) {
            cacheDataList.remove(cacheKey);
        }
    }
}
