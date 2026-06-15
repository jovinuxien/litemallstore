package org.linlinjava.litemall.goods.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Activates Redis as the durable staging buffer for raw CJ Dropshipping payloads
 * (see {@code CjRawCacheRepository}). Host/port/database are bound from
 * {@code redis.server.*} so they stay profile-overridable (localhost on the host,
 * the {@code redis} compose service inside docker) — no hardcoded host. Raw CJ
 * responses are JSON strings, so a {@link StringRedisTemplate} is sufficient.
 */
@Configuration
public class RedisConfig {

    @Value("${redis.server.host:localhost}")
    private String redisHost;

    @Value("${redis.server.port:6379}")
    private int redisPort;

    @Value("${redis.server.database:0}")
    private int redisDatabase;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(redisHost, redisPort);
        standalone.setDatabase(redisDatabase);
        return new JedisConnectionFactory(standalone);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
