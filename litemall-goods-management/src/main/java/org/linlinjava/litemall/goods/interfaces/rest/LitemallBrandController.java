package org.linlinjava.litemall.goods.interfaces.rest;

import jakarta.validation.constraints.NotNull;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.db.domain.LitemallBrand;
import org.linlinjava.litemall.goods.application.brand.BrandQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous customer brand surface (litemall-wx-api {@code /wx/brand} parity), served on
 * {@code /srv/brand}. Public per {@code litemall.svcsecurity.public-paths}. Thin: delegates to
 * {@link BrandQueryService} and wraps the wx-shaped result in the litemall envelope.
 */
@RestController
@RequestMapping("/srv/brand")
public class LitemallBrandController {

    private final BrandQueryService brandQueryService;

    public LitemallBrandController(BrandQueryService brandQueryService) {
        this.brandQueryService = brandQueryService;
    }

    @GetMapping("/list")
    public Object list(@RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit,
                       @Sort @RequestParam(defaultValue = "add_time") String sort,
                       @Order @RequestParam(defaultValue = "desc") String order) {
        return ResponseUtil.okList(brandQueryService.list(page, limit, sort, order));
    }

    @GetMapping("/detail")
    public Object detail(@NotNull Integer id) {
        LitemallBrand brand = brandQueryService.detail(id);
        if (brand == null) {
            return ResponseUtil.badArgumentValue();
        }
        return ResponseUtil.ok(brand);
    }
}
