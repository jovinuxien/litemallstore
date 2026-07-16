package org.linlinjava.litemall.gatewayadmin.web.admin;

import java.util.Map;

import org.linlinjava.litemall.db.service.LitemallSystemConfigService;
import org.linlinjava.litemall.gatewayadmin.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

import static org.linlinjava.litemall.gatewayadmin.web.admin.AdminEdge.blocking;

/**
 * System-config management (`litemall_system` table), ported from legacy
 * AdminConfigController. See {@link AdminEdge}.
 *
 * <p>Three groups only — mall, express, order. The legacy {@code wx} group is
 * deliberately NOT ported (WeChat is out of scope; the rows may not even
 * exist). The mall reader is misnamed {@code listMail()} in
 * LitemallSystemConfigService — that is the mall group.
 *
 * <p>POST validation: {@code updateConfig} silently no-ops unknown keys
 * (updateByExample on zero rows), so a misspelled key would "succeed" while
 * changing nothing. Every posted key is therefore checked BEFORE any update:
 * it must carry the group prefix AND already exist as a live row; otherwise
 * the whole request is rejected (402 naming the key) and no row is touched.
 *
 * <p>Runtime consumption note: services read these values through
 * litemall-core's static per-JVM SystemConfig cache (or their own copies), so
 * a POST here does NOT hot-reload running services — see
 * {@code docs/handoff-system-config-runtime.md}.
 */
@RestController
@RequestMapping("/srv/private/admin/config")
public class EdgeAdminConfigController {

    private final LitemallSystemConfigService systemConfigService;

    public EdgeAdminConfigController(LitemallSystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    // ---- mall (litemall_mall_*) ----

    @GetMapping("/mall")
    public Mono<ApiResponse<?>> listMall() {
        return blocking(() -> ApiResponse.ok(systemConfigService.listMail()));
    }

    @PostMapping("/mall")
    public Mono<ApiResponse<?>> updateMall(@RequestBody Map<String, String> data) {
        return blocking(() -> doUpdate("litemall_mall_", data));
    }

    // ---- express (litemall_express_*) ----

    @GetMapping("/express")
    public Mono<ApiResponse<?>> listExpress() {
        return blocking(() -> ApiResponse.ok(systemConfigService.listExpress()));
    }

    @PostMapping("/express")
    public Mono<ApiResponse<?>> updateExpress(@RequestBody Map<String, String> data) {
        return blocking(() -> doUpdate("litemall_express_", data));
    }

    // ---- order (litemall_order_*) ----

    @GetMapping("/order")
    public Mono<ApiResponse<?>> listOrder() {
        return blocking(() -> ApiResponse.ok(systemConfigService.listOrder()));
    }

    @PostMapping("/order")
    public Mono<ApiResponse<?>> updateOrder(@RequestBody Map<String, String> data) {
        return blocking(() -> doUpdate("litemall_order_", data));
    }

    // ---- brokerage (litemall_brokerage_*, Wave 5) ----
    // Rows are seeded by order's V<next>__brokerage_config.sql migration.
    // LitemallSystemConfigService has no listBrokerage(); the group is
    // filtered from queryAll() so litemall-db stays untouched (order owns
    // this wave's litemall-db edits). The key-validation guard below applies
    // unchanged: keys must exist as live rows or the POST is rejected.

    @GetMapping("/brokerage")
    public Mono<ApiResponse<?>> listBrokerage() {
        return blocking(() -> ApiResponse.ok(brokerageRows()));
    }

    @PostMapping("/brokerage")
    public Mono<ApiResponse<?>> updateBrokerage(@RequestBody Map<String, String> data) {
        return blocking(() -> doUpdate("litemall_brokerage_", data));
    }

    private Map<String, String> brokerageRows() {
        Map<String, String> data = new java.util.HashMap<>();
        for (Map.Entry<String, String> e : systemConfigService.queryAll().entrySet()) {
            if (e.getKey() != null && e.getKey().startsWith("litemall_brokerage_")) {
                data.put(e.getKey(), e.getValue());
            }
        }
        return data;
    }

    // ---- shared ----

    private ApiResponse<?> doUpdate(String prefix, Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return AdminEdge.badArgument();
        }
        Map<String, String> existing = existingFor(prefix);
        for (Map.Entry<String, String> entry : data.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(prefix)) {
                return ApiResponse.fail(AdminEdge.BAD_ARGUMENT_VALUE,
                        "key does not belong to this config group: " + key);
            }
            if (!existing.containsKey(key)) {
                return ApiResponse.fail(AdminEdge.BAD_ARGUMENT_VALUE,
                        "unknown config key (updates would be silently dropped): " + key);
            }
            if (entry.getValue() == null) {
                return ApiResponse.fail(AdminEdge.BAD_ARGUMENT_VALUE,
                        "config value must not be null: " + key);
            }
        }
        systemConfigService.updateConfig(data);
        return ApiResponse.ok(null);
    }

    private Map<String, String> existingFor(String prefix) {
        switch (prefix) {
            case "litemall_mall_":
                return systemConfigService.listMail();
            case "litemall_express_":
                return systemConfigService.listExpress();
            case "litemall_order_":
                return systemConfigService.listOrder();
            case "litemall_brokerage_":
                return brokerageRows();
            default:
                throw new IllegalArgumentException("unknown config group " + prefix);
        }
    }
}
