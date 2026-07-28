package org.linlinjava.litemall.goods.application.inventoryflow;

import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.db.service.LitemallGoodsProductService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Content enricher (Fisher et al. ch5): joins a per-pid {@link ProductFlowEvent} with its
 * promoted goods row, SKU stock sum and ranking signals into a {@link ProductInventoryContext}.
 * Local DB reads only — never a CJ call. Unresolvable pids (not promoted, or promote failed)
 * yield {@link ProductInventoryContext#unresolved} so downstream activators skip them.
 */
@Component
public class InventoryContextEnricher {

    private static final Logger log = LoggerFactory.getLogger(InventoryContextEnricher.class);

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final LitemallCjLinkageMapper linkageMapper;
    private final LitemallGoodsService goodsService;
    private final LitemallGoodsProductService productService;

    public InventoryContextEnricher(LitemallCjLinkageMapper linkageMapper,
                                    LitemallGoodsService goodsService,
                                    LitemallGoodsProductService productService) {
        this.linkageMapper = linkageMapper;
        this.goodsService = goodsService;
        this.productService = productService;
    }

    public ProductInventoryContext enrich(ProductFlowEvent event) {
        try {
            Integer goodsId = linkageMapper.findGoodsIdByCjPid(event.pid());
            if (goodsId == null) {
                return ProductInventoryContext.unresolved(event);
            }
            LitemallGoods goods = goodsService.findById(goodsId);
            if (goods == null) {
                return ProductInventoryContext.unresolved(event);
            }
            int stockTotal = 0;
            List<LitemallGoodsProduct> skus = productService.queryByGid(goodsId);
            for (LitemallGoodsProduct sku : skus) {
                if (sku.getNumber() != null) {
                    stockTotal += sku.getNumber();
                }
            }
            BigDecimal retail = goods.getRetailPrice();
            BigDecimal cost = captured(goods.getCost());
            BigDecimal marginPct = marginPct(retail, cost);
            return new ProductInventoryContext(event, goodsId, retail, cost, marginPct,
                    stockTotal, goods.getRating(), goods.getReviewCount());
        } catch (RuntimeException ex) {
            log.warn("inventory flow: context enrich failed for pid {}: {}", event.pid(), ex.getMessage());
            return ProductInventoryContext.unresolved(event);
        }
    }

    /** The V2 column default 0.00 means "not captured" — normalize to null, never a fake cost. */
    private static BigDecimal captured(BigDecimal cost) {
        return cost != null && cost.signum() > 0 ? cost : null;
    }

    private static BigDecimal marginPct(BigDecimal retail, BigDecimal cost) {
        if (retail == null || retail.signum() <= 0 || cost == null) {
            return null;
        }
        return retail.subtract(cost)
                .multiply(HUNDRED)
                .divide(retail, 2, RoundingMode.HALF_UP);
    }
}
