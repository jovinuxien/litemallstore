package org.linlinjava.litemall.goods.interfaces.rest.admin;

import jakarta.validation.constraints.NotNull;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.db.domain.LitemallBrand;
import org.linlinjava.litemall.db.service.LitemallBrandService;
import org.linlinjava.litemall.goods.application.attribution.AttributionProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Admin brand management, ported from litemall-admin-api ({@code admin.web.AdminBrandController}).
 * Mounted under {@code /srv/private/admin/**} (ROLE_ADMIN-gated by litemall-svcsecurity).
 */
@RestController
@RequestMapping("/srv/private/admin/brand")
@Validated
public class AdminBrandController {
    private final Log logger = LogFactory.getLog(AdminBrandController.class);

    @Autowired
    private LitemallBrandService brandService;

    @GetMapping("/list")
    public Object list(String id, String name,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit,
                       @Sort @RequestParam(defaultValue = "add_time") String sort,
                       @Order @RequestParam(defaultValue = "desc") String order) {
        List<LitemallBrand> brandList = brandService.querySelective(id, name, page, limit, sort, order);
        brandService.attachGoodsCounts(brandList);
        return ResponseUtil.okList(brandList);
    }

    /**
     * Wave 25: {@code desc} is no longer required — provider-captured store rows carry an empty
     * desc, and the curation flow (rename + display toggle) must not be blocked on it.
     */
    private Object validate(LitemallBrand brand) {
        String name = brand.getName();
        if (StringUtils.isEmpty(name)) {
            return ResponseUtil.badArgument();
        }
        return null;
    }

    @PostMapping("/create")
    public Object create(@RequestBody LitemallBrand brand) {
        Object error = validate(brand);
        if (error != null) {
            return error;
        }
        BigDecimal price = brand.getFloorPrice();
        if (price == null) {
            return ResponseUtil.badArgument();
        }
        // Manual create: admins own only source='manual' rows; provider rows arrive exclusively
        // via enrichment. Manual rows default display-enabled (V60 contract).
        brand.setId(null);
        brand.setSource(AttributionProvider.SOURCE_MANUAL);
        brand.setExternalId(null);
        if (brand.getKind() == null) {
            brand.setKind((byte) 0);
        }
        if (brand.getDisplayEnabled() == null) {
            brand.setDisplayEnabled(Boolean.TRUE);
        }
        brandService.add(brand);
        return ResponseUtil.ok(brand);
    }

    @GetMapping("/read")
    public Object read(@NotNull Integer id) {
        LitemallBrand brand = brandService.findById(id);
        return ResponseUtil.ok(brand);
    }

    /**
     * The Wave-25 curation vehicle: rename (raw supplier legal names → customer-worthy store
     * names) and toggle {@code displayEnabled}. Provider identity keys are never admin-editable —
     * stripped so a stale form echo can't clobber how a row is matched at the next enrichment.
     */
    @PostMapping("/update")
    public Object update(@RequestBody LitemallBrand brand) {
        if (brand.getId() == null) {
            return ResponseUtil.badArgument();
        }
        Object error = validate(brand);
        if (error != null) {
            return error;
        }
        brand.setSource(null);
        brand.setExternalId(null);
        if (brandService.updateById(brand) == 0) {
            return ResponseUtil.updatedDataFailed();
        }
        return ResponseUtil.ok(brand);
    }

    @PostMapping("/delete")
    public Object delete(@RequestBody LitemallBrand brand) {
        Integer id = brand.getId();
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        brandService.deleteById(id);
        return ResponseUtil.ok();
    }
}
