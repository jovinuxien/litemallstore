package org.linlinjava.litemall.goods.infrastructure.acl.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * Durable staging buffer for RAW CJ Dropshipping API payloads, backed by Redis.
 *
 * <p>This replaces the per-instance in-memory cache that {@code CJProductService} used to hold: a
 * rate-limited CJ fetch lands its raw JSON here (keyed by category/page or pid), so it survives a
 * restart and a re-index never needs to re-hit the 1-request/300s CJ API while the entry is live.
 * The {@code CjSnapshotSyncService} reads these payloads back to normalize + persist them into
 * {@code litemall_cj_product}.
 *
 * <p>Redis is treated as best-effort: any Redis failure is logged and degrades to a cache MISS
 * (so the caller falls back to a live, paced fetch) rather than breaking the pipeline.
 */
@Repository
public class CjRawCacheRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(CjRawCacheRepository.class);

    private static final String PREFIX = "cj:raw:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CJDropshippingConfig config;

    public CjRawCacheRepository(StringRedisTemplate redis,
                                ObjectMapper objectMapper,
                                CJDropshippingConfig config) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    /** Key for a single CJ {@code /product/list} page within a category. */
    public static String listKey(String categoryId, int page) {
        return PREFIX + "list:" + categoryId + ":" + page;
    }

    /** Key for the legacy single-page (no-category) product list. */
    public static String defaultListKey() {
        return PREFIX + "list:default";
    }

    /** Key for the CJ category tree. */
    public static String categoriesKey() {
        return PREFIX + "categories";
    }

    /** Key for a single CJ product detail by raw pid. */
    public static String detailKey(String pid) {
        return PREFIX + "detail:" + pid;
    }

    /** Read and deserialize a cached payload, or empty on a miss / Redis error / parse error. */
    public <T> Optional<T> get(String key, Class<T> type) {
        String json;
        try {
            json = redis.opsForValue().get(key);
        } catch (RuntimeException ex) {
            LOGGER.warn("Redis unavailable reading '{}' ({}); treating as cache miss", key, ex.getMessage());
            return Optional.empty();
        }
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(objectMapper.readValue(json, type));
        } catch (Exception ex) {
            LOGGER.warn("Failed to parse cached CJ payload '{}': {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    /** Serialize and store a payload under the configured raw TTL; Redis errors are swallowed (logged). */
    public void put(String key, Object value) {
        if (value == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            redis.opsForValue().set(key, json, Duration.ofSeconds(config.getRedis().getRawTtlSeconds()));
        } catch (Exception ex) {
            LOGGER.warn("Failed to cache CJ payload '{}': {}", key, ex.getMessage());
        }
    }
}
