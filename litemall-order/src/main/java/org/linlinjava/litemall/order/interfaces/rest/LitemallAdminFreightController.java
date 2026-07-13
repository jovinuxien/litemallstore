package org.linlinjava.litemall.order.interfaces.rest;

import lombok.Data;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallShippingTemplate;
import org.linlinjava.litemall.db.domain.LitemallShippingTemplateFree;
import org.linlinjava.litemall.db.domain.LitemallShippingTemplateRegion;
import org.linlinjava.litemall.order.application.internal.FreightCalculationService;
import org.linlinjava.litemall.order.application.internal.FreightTemplateAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin freight-template surface (Wave 4, Task A), served under
 * {@code /srv/private/admin/freight/**} — the edge gateway gates the prefix to
 * ROLE_ADMIN (machine token) exactly like {@code /srv/private/admin/order/**};
 * this controller adds no auth of its own.
 *
 * <p>Envelope conventions match the other admin controllers: {@code ResponseUtil}
 * {@code {errno, errmsg, data}}; client errors that must stop the SPA (delete while
 * referenced, unknown id) are HTTP 422 with {@code errno:422}. {@code /selectlist}
 * returns a BARE array (the admin SPA's dropdown convention). Contract:
 * docs/handoff-gateway-admin-freight.md.
 */
@RestController
@RequestMapping("/srv/private/admin/freight")
public class LitemallAdminFreightController {

    private final FreightTemplateAdminService adminService;
    private final FreightCalculationService calculationService;

    public LitemallAdminFreightController(FreightTemplateAdminService adminService,
                                          FreightCalculationService calculationService) {
        this.adminService = adminService;
        this.calculationService = calculationService;
    }

    /** All templates (non-deleted) with their rule counts: { list, total }. */
    @GetMapping("/list")
    public Object list() {
        List<LitemallShippingTemplate> templates = adminService.list();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LitemallShippingTemplate t : templates) {
            Map<String, Object> row = toRow(t);
            row.put("regionCount", adminService.regions(t.getId()).size());
            row.put("freeRuleCount", adminService.freeRules(t.getId()).size());
            rows.add(row);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", rows);
        data.put("total", rows.size());
        return ResponseUtil.ok(data);
    }

    /** BARE array of {id, name, isDefault} for pickers (admin SPA selectlist convention). */
    @GetMapping("/selectlist")
    public Object selectlist() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LitemallShippingTemplate t : adminService.list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", t.getId());
            row.put("name", t.getName());
            row.put("isDefault", Boolean.TRUE.equals(t.getIsDefault()));
            rows.add(row);
        }
        return rows;
    }

    /** Template detail: { template, regions, freeRules }. Unknown id → 422. */
    @GetMapping("/detail")
    public Object detail(@RequestParam Integer id) {
        LitemallShippingTemplate template = adminService.template(id);
        if (template == null) {
            return unprocessable("Freight template " + id + " not found");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("template", toRow(template));
        data.put("regions", adminService.regions(id));
        data.put("freeRules", adminService.freeRules(id));
        return ResponseUtil.ok(data);
    }

    /** Create a template with its region rows + free rules; returns { id }. */
    @PostMapping("/create")
    public Object create(@RequestBody TemplatePayload payload) {
        String problem = payload.validate();
        if (problem != null) {
            return unprocessable(problem);
        }
        Integer id = adminService.create(payload.toTemplate(null), payload.toRegions(), payload.toFreeRules());
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        return ResponseUtil.ok(data);
    }

    /** Update a template; child rows are replaced wholesale. Unknown id → 422. */
    @PostMapping("/update")
    public Object update(@RequestBody TemplatePayload payload) {
        if (payload.getId() == null) {
            return unprocessable("id is required");
        }
        String problem = payload.validate();
        if (problem != null) {
            return unprocessable(problem);
        }
        FreightTemplateAdminService.MutationResult result = adminService.update(
                payload.toTemplate(payload.getId()), payload.toRegions(), payload.toFreeRules());
        if (result == FreightTemplateAdminService.MutationResult.NOT_FOUND) {
            return unprocessable("Freight template " + payload.getId() + " not found");
        }
        return ResponseUtil.ok();
    }

    /** Delete a template — refused (422) while a live goods still references it. */
    @PostMapping("/delete")
    public Object delete(@RequestBody Map<String, Integer> body) {
        Integer id = body.get("id");
        if (id == null) {
            return unprocessable("id is required");
        }
        FreightTemplateAdminService.MutationResult result = adminService.delete(id);
        switch (result) {
            case NOT_FOUND:
                return unprocessable("Freight template " + id + " not found");
            case REFERENCED:
                return unprocessable("Freight template " + id + " is still referenced by live goods"
                        + " — rebind or delist them first");
            default:
                return ResponseUtil.ok();
        }
    }

    /** Make a template the default for unbound goods (id 0 clears the default). */
    @PostMapping("/set-default")
    public Object setDefault(@RequestBody Map<String, Integer> body) {
        Integer id = body.get("id");
        if (id == null) {
            return unprocessable("id is required");
        }
        FreightTemplateAdminService.MutationResult result = adminService.setDefault(id);
        if (result == FreightTemplateAdminService.MutationResult.NOT_FOUND) {
            return unprocessable("Freight template " + id + " not found");
        }
        return ResponseUtil.ok();
    }

    /**
     * Dry-run: price a hypothetical group of {@code quantity} units / {@code amount} value
     * against one template for a destination — lets an admin sanity-check a template before
     * binding goods to it. Unknown template → 422.
     */
    @GetMapping("/preview")
    public Object preview(@RequestParam Integer tempId,
                          @RequestParam(defaultValue = "1") Integer quantity,
                          @RequestParam(required = false) BigDecimal amount,
                          @RequestParam(required = false) String countryCode,
                          @RequestParam(required = false) String provinceName) {
        FreightCalculationService.BreakdownEntry entry = calculationService.previewTemplate(
                tempId, quantity, amount, countryCode, provinceName);
        if (entry == null) {
            return unprocessable("Freight template " + tempId + " not found");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("templateId", entry.getTemplateId());
        data.put("templateName", entry.getTemplateName());
        data.put("source", entry.getSource());
        data.put("amount", entry.getAmount());
        data.put("note", entry.getNote());
        return ResponseUtil.ok(data);
    }

    // ---- shaping ---------------------------------------------------------------------

    private static ResponseEntity<Object> unprocessable(String message) {
        return ResponseEntity.unprocessableEntity().body(ResponseUtil.fail(422, message));
    }

    private static Map<String, Object> toRow(LitemallShippingTemplate t) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", t.getId());
        row.put("name", t.getName());
        row.put("type", t.getType());
        row.put("appoint", t.getAppoint());
        row.put("isDefault", Boolean.TRUE.equals(t.getIsDefault()));
        row.put("sort", t.getSort());
        row.put("addTime", t.getAddTime());
        row.put("updateTime", t.getUpdateTime());
        return row;
    }

    /** Create/update body: template header + full child-row sets. */
    @Data
    public static class TemplatePayload {
        private Integer id;
        private String name;
        /** Billing type 1=per piece (2/3 weight/volume accepted but billed per piece — v1). */
        private Integer type = 1;
        /** 0 = no free rules; >=1 = free rules active (OR semantics). */
        private Integer appoint = 0;
        private Integer sort = 0;
        private List<RegionRow> regions;
        private List<FreeRow> freeRules;

        String validate() {
            if (name == null || name.isBlank()) {
                return "name is required";
            }
            if (type != null && (type < 1 || type > 3)) {
                return "type must be 1 (piece), 2 (weight) or 3 (volume)";
            }
            if (appoint != null && (appoint < 0 || appoint > 2)) {
                return "appoint must be 0, 1 or 2";
            }
            return null;
        }

        LitemallShippingTemplate toTemplate(Integer id) {
            LitemallShippingTemplate t = new LitemallShippingTemplate();
            t.setId(id);
            t.setName(name);
            t.setType(type == null ? 1 : type);
            t.setAppoint(appoint == null ? 0 : appoint);
            t.setSort(sort == null ? 0 : sort);
            return t;
        }

        List<LitemallShippingTemplateRegion> toRegions() {
            List<LitemallShippingTemplateRegion> rows = new ArrayList<>();
            if (regions != null) {
                for (RegionRow r : regions) {
                    LitemallShippingTemplateRegion row = new LitemallShippingTemplateRegion();
                    row.setCountryCode(r.getCountryCode());
                    row.setProvinceName(r.getProvinceName());
                    row.setFirst(r.getFirst());
                    row.setFirstPrice(r.getFirstPrice());
                    row.setContinueP(r.getContinueP());
                    row.setContinuePrice(r.getContinuePrice());
                    rows.add(row);
                }
            }
            return rows;
        }

        List<LitemallShippingTemplateFree> toFreeRules() {
            List<LitemallShippingTemplateFree> rows = new ArrayList<>();
            if (freeRules != null) {
                for (FreeRow f : freeRules) {
                    LitemallShippingTemplateFree row = new LitemallShippingTemplateFree();
                    row.setCountryCode(f.getCountryCode());
                    row.setProvinceName(f.getProvinceName());
                    row.setNumber(f.getNumber());
                    row.setPrice(f.getPrice());
                    rows.add(row);
                }
            }
            return rows;
        }
    }

    @Data
    public static class RegionRow {
        /** ISO country or '*' (default when absent). */
        private String countryCode;
        /** Free-text province/state; null = whole country. */
        private String provinceName;
        private BigDecimal first;
        private BigDecimal firstPrice;
        private BigDecimal continueP;
        private BigDecimal continuePrice;
    }

    @Data
    public static class FreeRow {
        private String countryCode;
        private String provinceName;
        /** Free when the group has at least this many units (0 = not used). */
        private BigDecimal number;
        /** Free when the group amount reaches this value (0 = not used). */
        private BigDecimal price;
    }
}
