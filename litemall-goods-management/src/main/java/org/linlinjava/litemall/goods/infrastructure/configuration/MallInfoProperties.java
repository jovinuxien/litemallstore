package org.linlinjava.litemall.goods.infrastructure.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Storefront "about" metadata (litemall-wx-api {@code /wx/home/about} parity). In the original
 * monolith these live in the {@code litemall_system} config table; here they are config-driven under
 * {@code litemall.mall.*} so the anonymous {@code /srv/home/about} endpoint resolves without a
 * system-config dependency. Profile/config-server overridable.
 */
@Component
@Data
@ConfigurationProperties(prefix = "litemall.mall")
public class MallInfoProperties {
    private String name = "litemall";
    private String address = "";
    private String phone = "";
    private String qq = "";
    private String longitude = "";
    private String latitude = "";
}
