package org.linlinjava.litemall.goods.interfaces.rest.admin;

import jakarta.validation.constraints.NotBlank;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallCjSourcingRequest;
import org.linlinjava.litemall.goods.application.goods.cj.CjSourcingService;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.warehouse.CJWarehouseDetailResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Admin surface for the CJ catalog-side verticals (Wave 3): product sourcing and
 * warehouse/storage lookup. Path-gated to ROLE_ADMIN by litemall-svcsecurity
 * ({@code /srv/private/admin/**} is deny-by-default) — no method-level checks needed.
 * CJ-unreachable degrades to a clean errmsg, never a raw 500 or a hang (the CJ clients
 * carry explicit timeouts).
 */
@RestController
@RequestMapping("/srv/private/admin/cj")
@Validated
public class AdminCjController {

    private final CjSourcingService sourcingService;
    private final CJProductService cjProductService;

    public AdminCjController(CjSourcingService sourcingService, CJProductService cjProductService) {
        this.sourcingService = sourcingService;
        this.cjProductService = cjProductService;
    }

    /** Body for POST /sourcing: a cjPid from our catalog, or explicit name+image (CJ requires both). */
    public record SourcingCreateBody(String cjPid, String productName, String productImage,
                                     String productUrl, BigDecimal price, String remark) {}

    @PostMapping("/sourcing")
    public Object createSourcing(@RequestBody SourcingCreateBody body) {
        if (body == null) {
            return ResponseUtil.badArgument();
        }
        try {
            LitemallCjSourcingRequest row = sourcingService.create(body.cjPid(), body.productName(),
                    body.productImage(), body.productUrl(), body.price(), body.remark());
            return ResponseUtil.ok(row);
        } catch (CjSourcingService.BadSourcingRequestException ex) {
            return ResponseUtil.fail(402, ex.getMessage());
        }
    }

    /**
     * Local sourcing list (survives restarts, CJ-down safe). {@code sourceId} narrows to one
     * request; {@code refresh=true} re-queries CJ for the freshest status before answering.
     */
    @GetMapping("/sourcing")
    public Object querySourcing(@RequestParam(required = false) String sourceId,
                                @RequestParam(defaultValue = "false") Boolean refresh,
                                @RequestParam(defaultValue = "1") Integer page,
                                @RequestParam(defaultValue = "10") Integer limit) {
        if (sourceId != null && !sourceId.isBlank()) {
            LitemallCjSourcingRequest row = sourcingService.bySourceId(sourceId.trim(), Boolean.TRUE.equals(refresh));
            return row != null ? ResponseUtil.ok(row)
                    : ResponseUtil.fail(402, "no sourcing request with sourceId " + sourceId);
        }
        return ResponseUtil.ok(sourcingService.list(page, limit, Boolean.TRUE.equals(refresh)));
    }

    /** CJ warehouse/storage info by storageId; a CJ-side miss or outage is a clean errmsg. */
    @GetMapping("/warehouse")
    public Object warehouseDetail(@NotBlank @RequestParam String id) {
        CJWarehouseDetailResponse response = cjProductService.getWarehouseDetail(id.trim());
        if (response == null) {
            return ResponseUtil.fail(502, "CJ warehouse lookup unavailable, try again later");
        }
        if (!response.isOk() || response.getData() == null) {
            return ResponseUtil.fail(402, response.getMessage() != null
                    ? response.getMessage() : "warehouse not found");
        }
        return ResponseUtil.ok(response.getData());
    }
}
