package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.db.domain.LitemallStore;
import org.linlinjava.litemall.order.application.internal.LitemallStoreServiceLayer;
import org.linlinjava.litemall.order.domain.model.valueobjects.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Customer store directory (Wave 4, Task B): the pickup-store picker at checkout and
 * the store page. ANONYMOUS — {@code /srv/store/**} is on this service's svcsecurity
 * public-paths (browsing stores needs no identity); only visible ({@code is_show=1})
 * stores are returned, and admin-only fields are never serialized.
 */
@RestController
@RequestMapping("/srv/store")
public class LitemallStoreController {

    private final LitemallStoreServiceLayer storeServiceLayer;

    public LitemallStoreController(LitemallStoreServiceLayer storeServiceLayer) {
        this.storeServiceLayer = storeServiceLayer;
    }

    /** Visible stores: { list, total }. */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list() {
        List<Map<String, Object>> rows = storeServiceLayer.listVisible().stream()
                .map(LitemallStoreController::toCustomerRow)
                .collect(Collectors.toList());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", rows.size());
        return ApiResponse.ok(data);
    }

    /** One visible store; hidden/unknown → 404 envelope. */
    @GetMapping("/detail")
    public ApiResponse<Map<String, Object>> detail(@RequestParam Integer id) {
        LitemallStore store = storeServiceLayer.findById(id);
        if (store == null || !Boolean.TRUE.equals(store.getIsShow())) {
            return ApiResponse.fail(404, "Store not found");
        }
        return ApiResponse.ok(toCustomerRow(store));
    }

    private static Map<String, Object> toCustomerRow(LitemallStore store) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", store.getId());
        row.put("name", store.getName());
        row.put("intro", store.getIntro());
        row.put("phone", store.getPhone());
        row.put("address", store.getAddress());
        row.put("detailedAddress", store.getDetailedAddress());
        row.put("logo", store.getLogo());
        row.put("latitude", store.getLatitude());
        row.put("longitude", store.getLongitude());
        row.put("businessHours", store.getBusinessHours());
        return row;
    }
}
