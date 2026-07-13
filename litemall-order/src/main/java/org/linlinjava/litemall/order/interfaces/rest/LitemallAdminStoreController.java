package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallStore;
import org.linlinjava.litemall.order.application.internal.LitemallStoreServiceLayer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin store CRUD (Wave 4, Task B), under {@code /srv/private/admin/store/**} — the
 * edge gateway gates the prefix to ROLE_ADMIN (machine token); no auth here. Envelope:
 * {@code ResponseUtil}; hard client errors are HTTP 422 with {@code errno: 422}.
 * Contract: docs/handoff-gateway-admin-store-writeoff.md.
 */
@RestController
@RequestMapping("/srv/private/admin/store")
public class LitemallAdminStoreController {

    private final LitemallStoreServiceLayer storeServiceLayer;

    public LitemallAdminStoreController(LitemallStoreServiceLayer storeServiceLayer) {
        this.storeServiceLayer = storeServiceLayer;
    }

    /** All non-deleted stores (hidden included): { list, total }. */
    @GetMapping("/list")
    public Object list() {
        List<LitemallStore> stores = storeServiceLayer.listAll();
        Map<String, Object> data = new HashMap<>();
        data.put("list", stores);
        data.put("total", stores.size());
        return ResponseUtil.ok(data);
    }

    /** One store (hidden included); unknown → 422. */
    @GetMapping("/detail")
    public Object detail(@RequestParam Integer id) {
        LitemallStore store = storeServiceLayer.findById(id);
        return store == null ? unprocessable("Store " + id + " not found") : ResponseUtil.ok(store);
    }

    /** Create a store; returns { id }. isShow defaults to true. */
    @PostMapping("/create")
    public Object create(@RequestBody LitemallStore store) {
        if (store.getName() == null || store.getName().isBlank()) {
            return unprocessable("name is required");
        }
        Integer id = storeServiceLayer.create(store);
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        return ResponseUtil.ok(data);
    }

    /** Full update by id (including isShow visibility toggling); unknown id → 422. */
    @PostMapping("/update")
    public Object update(@RequestBody LitemallStore store) {
        if (store.getId() == null) {
            return unprocessable("id is required");
        }
        if (store.getName() == null || store.getName().isBlank()) {
            return unprocessable("name is required");
        }
        return storeServiceLayer.update(store)
                ? ResponseUtil.ok()
                : unprocessable("Store " + store.getId() + " not found");
    }

    /**
     * Logical delete. Historical pickup orders keep rendering (store rows are only
     * flagged deleted); NEW pickup submits against the store 422 immediately.
     */
    @PostMapping("/delete")
    public Object delete(@RequestBody Map<String, Integer> body) {
        Integer id = body.get("id");
        if (id == null) {
            return unprocessable("id is required");
        }
        return storeServiceLayer.delete(id)
                ? ResponseUtil.ok()
                : unprocessable("Store " + id + " not found");
    }

    private static ResponseEntity<Object> unprocessable(String message) {
        return ResponseEntity.unprocessableEntity().body(ResponseUtil.fail(422, message));
    }
}
