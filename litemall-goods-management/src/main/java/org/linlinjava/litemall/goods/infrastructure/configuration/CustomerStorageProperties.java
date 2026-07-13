package org.linlinjava.litemall.goods.infrastructure.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Caps for the customer-facing upload endpoint ({@code POST /srv/storage/upload}), configured under
 * {@code litemall.customer-storage.*}. Kept separate from the admin storage surface so customer
 * limits can be tightened without touching admin uploads. Profile/config-server overridable.
 */
@Component
@Data
@ConfigurationProperties(prefix = "litemall.customer-storage")
public class CustomerStorageProperties {
    /** Maximum accepted upload size in bytes (default 5 MB). */
    private long maxSizeBytes = 5242880L;
}
