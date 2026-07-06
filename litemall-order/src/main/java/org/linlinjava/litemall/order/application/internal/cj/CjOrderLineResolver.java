package org.linlinjava.litemall.order.application.internal.cj;

import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.db.service.LitemallGoodsProductService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Recovers the CJ variant id ({@code cj_vid}) for a checkout line straight off the native DB rows,
 * the payoff of the CJ→native catalog merge: a CJ product now lives in {@code litemall_goods}
 * ({@code source='cj'}) and each SKU in {@code litemall_goods_product} carries its {@code cj_vid}.
 * Checkout therefore identifies a line by the ordinary native {@code productId} it already holds and
 * the order service looks up the real CJ vid here — no {@code cj_<pid>} string parsing, no
 * {@code vid}-as-productId hack on the caller.
 *
 * <p>A line resolves to a CJ vid ONLY when the product exists, its parent goods is {@code source='cj'},
 * and a non-blank {@code cj_vid} is present; anything else is a hard {@link LitemallCjOrderException}
 * so a non-CJ (or unmapped) line can never be silently shipped to CJ {@code createOrder}.
 */
@Service
public class CjOrderLineResolver {

    private static final Logger log = LoggerFactory.getLogger(CjOrderLineResolver.class);
    /** {@code litemall_goods.source} value marking a CJ-sourced row (mirrors the catalog merge). */
    private static final String SOURCE_CJ = "cj";

    private final LitemallGoodsProductService productService;
    private final LitemallGoodsService goodsService;

    public CjOrderLineResolver(LitemallGoodsProductService productService,
                               LitemallGoodsService goodsService) {
        this.productService = productService;
        this.goodsService = goodsService;
    }

    /**
     * Recover the CJ {@code cj_vid} for a native {@code litemall_goods_product.id}, after confirming the
     * SKU's parent goods is {@code source='cj'}. Throws {@link LitemallCjOrderException} if the product
     * is missing, not CJ-sourced, or has no {@code cj_vid} on the row.
     */
    public String resolveVid(Integer productId) {
        if (productId == null) {
            throw new LitemallCjOrderException("CJ line is missing a productId");
        }
        LitemallGoodsProduct product = productService.findById(productId);
        if (product == null) {
            throw new LitemallCjOrderException("no product for id " + productId);
        }
        LitemallGoods goods = goodsService.findById(product.getGoodsId());
        if (goods == null || !SOURCE_CJ.equals(goods.getSource())) {
            throw new LitemallCjOrderException(
                    "product " + productId + " is not a CJ-sourced item; cannot place a CJ order for it");
        }
        String cjVid = product.getCjVid();
        if (cjVid == null || cjVid.isBlank()) {
            throw new LitemallCjOrderException(
                    "CJ product " + productId + " (goods " + product.getGoodsId() + ") has no cj_vid on the row");
        }
        log.debug("Resolved CJ line productId={} -> cj_vid={} (goods {})", productId, cjVid, product.getGoodsId());
        return cjVid;
    }
}
