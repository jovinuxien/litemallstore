package org.linlinjava.litemall.goods.application.brand;

import org.linlinjava.litemall.db.domain.LitemallBrand;
import org.linlinjava.litemall.db.service.LitemallBrandService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Anonymous customer read queries for brands (litemall-wx-api {@code WxBrandController} parity).
 *
 * <p>Altitude: goods-core/write paths use aggregates (e.g. {@code LitemallGoodsAggregate}); these
 * read-only, catalog-adjacent customer lists go controller → this application query service →
 * {@code litemall-db} service, returning the same shape the wx-api emits (the SPA's expected
 * contract). No new aggregate/repository is introduced for a read. The paged {@code list} returns
 * the PageHelper-backed list so {@code ResponseUtil.okList} reports the true total.
 */
@Service
public class BrandQueryService {

    private final LitemallBrandService brandService;

    public BrandQueryService(LitemallBrandService brandService) {
        this.brandService = brandService;
    }

    /** Paginated brand list (PageHelper-backed; total preserved for okList). */
    public List<LitemallBrand> list(Integer page, Integer limit, String sort, String order) {
        return brandService.query(page, limit, sort, order);
    }

    /** A single brand by id, or null when absent. */
    public LitemallBrand detail(Integer id) {
        if (id == null) {
            return null;
        }
        return brandService.findById(id);
    }
}
