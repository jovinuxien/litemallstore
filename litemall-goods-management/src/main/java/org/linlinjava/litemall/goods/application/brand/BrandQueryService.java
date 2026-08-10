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

    /**
     * Paginated brand/store list (PageHelper-backed; total preserved for okList). Wave 25: only
     * display-enabled rows — provider-captured raw supplier names stay hidden until an admin
     * curates them — and each row carries {@code kind} (Store vs Brand badge) + the computed
     * {@code goodsCount} (lets the SPA hide empty brands without its N+1 probe).
     */
    public List<LitemallBrand> list(Integer page, Integer limit, String sort, String order) {
        List<LitemallBrand> brands = brandService.queryDisplayEnabled(page, limit, sort, order);
        brandService.attachGoodsCounts(brands);
        return brands;
    }

    /**
     * A single brand by id, or null when absent, deleted, or not display-enabled (the curation
     * gate applies to the public read exactly as to the list).
     */
    public LitemallBrand detail(Integer id) {
        if (id == null) {
            return null;
        }
        LitemallBrand brand = brandService.findById(id);
        if (brand == null
                || Boolean.TRUE.equals(brand.getDeleted())
                || !Boolean.TRUE.equals(brand.getDisplayEnabled())) {
            return null;
        }
        brandService.attachGoodsCounts(List.of(brand));
        return brand;
    }
}
